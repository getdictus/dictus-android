// N-gram prediction engine for Dictus. Apache 2.0.
#include "dictus_ngram.h"
#include "dictus_ngram_format.h"

#include <algorithm>
#include <cstring>
#include <fcntl.h>
#include <limits>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

namespace dictus {

namespace {
constexpr size_t MAX_CONTEXT_BYTES = 255;
constexpr size_t MAX_PREDICTION_BYTES = 255;
constexpr size_t MAX_FILE_BYTES = 64U * 1024U * 1024U;
constexpr uint32_t MAX_SECTION_ENTRIES = 250000U;
constexpr uint32_t BACKOFF_NUMERATOR = 4;
constexpr uint32_t BACKOFF_DENOMINATOR = 10;

bool isValidUtf8(const uint8_t* bytes, size_t length) {
    size_t index = 0;
    while (index < length) {
        const uint8_t first = bytes[index];
        size_t continuationCount = 0;
        uint32_t codePoint = 0;
        if (first <= 0x7fU) {
            ++index;
            continue;
        } else if (first >= 0xc2U && first <= 0xdfU) {
            continuationCount = 1;
            codePoint = first & 0x1fU;
        } else if (first >= 0xe0U && first <= 0xefU) {
            continuationCount = 2;
            codePoint = first & 0x0fU;
        } else if (first >= 0xf0U && first <= 0xf4U) {
            continuationCount = 3;
            codePoint = first & 0x07U;
        } else {
            return false;
        }
        if (continuationCount > length - index - 1U) return false;
        for (size_t continuation = 1; continuation <= continuationCount; ++continuation) {
            const uint8_t byte = bytes[index + continuation];
            if ((byte & 0xc0U) != 0x80U) return false;
            codePoint = (codePoint << 6U) | (byte & 0x3fU);
        }
        if ((continuationCount == 2 && codePoint < 0x800U) ||
            (continuationCount == 3 && codePoint < 0x10000U) ||
            codePoint > 0x10ffffU || (codePoint >= 0xd800U && codePoint <= 0xdfffU)) {
            return false;
        }
        index += continuationCount + 1U;
    }
    return true;
}
}

NgramEngine::NgramEngine()
    : data_(nullptr),
      dataSize_(0),
      stringTableOffset_(0),
      stringTableSize_(0),
      loaded_(false) {}

NgramEngine::~NgramEngine() {
    unload();
}

uint16_t NgramEngine::readLe16(const uint8_t* pointer) {
    return static_cast<uint16_t>(pointer[0]) |
        static_cast<uint16_t>(static_cast<uint16_t>(pointer[1]) << 8U);
}

uint32_t NgramEngine::readLe32(const uint8_t* pointer) {
    return static_cast<uint32_t>(pointer[0]) |
        (static_cast<uint32_t>(pointer[1]) << 8U) |
        (static_cast<uint32_t>(pointer[2]) << 16U) |
        (static_cast<uint32_t>(pointer[3]) << 24U);
}

bool NgramEngine::load(const char* path) {
    unload();
    if (path == nullptr) return false;

    const int descriptor = open(path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (descriptor < 0) return false;

    struct stat metadata {};
    if (fstat(descriptor, &metadata) != 0 || metadata.st_size < 0 ||
        !S_ISREG(metadata.st_mode) ||
        static_cast<uintmax_t>(metadata.st_size) > std::numeric_limits<size_t>::max()) {
        close(descriptor);
        return false;
    }
    const size_t fileSize = static_cast<size_t>(metadata.st_size);
    if (fileSize < NGRM_HEADER_SIZE || fileSize > MAX_FILE_BYTES) {
        close(descriptor);
        return false;
    }

    void* mapped = mmap(nullptr, fileSize, PROT_READ, MAP_PRIVATE, descriptor, 0);
    close(descriptor);
    if (mapped == MAP_FAILED) return false;

    data_ = static_cast<uint8_t*>(mapped);
    dataSize_ = fileSize;
    madvise(mapped, fileSize, MADV_RANDOM);

    bool valid = false;
    try {
        valid = validateAndBuildIndices();
    } catch (const std::bad_alloc&) {
        valid = false;
    }
    if (!valid) {
        unload();
        return false;
    }
    loaded_ = true;
    return true;
}

void NgramEngine::unload() {
    bigramIndex_.clear();
    trigramIndex_.clear();
    if (data_ != nullptr) {
        munmap(data_, dataSize_);
        data_ = nullptr;
    }
    dataSize_ = 0;
    stringTableOffset_ = 0;
    stringTableSize_ = 0;
    loaded_ = false;
}

bool NgramEngine::isLoaded() const {
    return loaded_;
}

bool NgramEngine::validateAndBuildIndices() {
    if (std::memcmp(data_, NGRM_MAGIC, sizeof(NGRM_MAGIC)) != 0 ||
        readLe16(data_ + 4) != NGRM_VERSION || readLe16(data_ + 6) != 0) {
        return false;
    }

    const uint32_t bigramCount = readLe32(data_ + 8);
    const uint32_t trigramCount = readLe32(data_ + 12);
    const size_t bigramOffset = readLe32(data_ + 16);
    const size_t trigramOffset = readLe32(data_ + 20);
    const size_t stringOffset = readLe32(data_ + 24);
    const size_t stringSize = readLe32(data_ + 28);

    if (bigramOffset < NGRM_HEADER_SIZE || bigramOffset > trigramOffset ||
        trigramOffset > stringOffset || stringOffset > dataSize_ ||
        stringSize > dataSize_ - stringOffset || stringOffset + stringSize != dataSize_) {
        return false;
    }

    stringTableOffset_ = stringOffset;
    stringTableSize_ = stringSize;
    if (!buildIndex(bigramOffset, trigramOffset, bigramCount, bigramIndex_) ||
        !buildIndex(trigramOffset, stringOffset, trigramCount, trigramIndex_)) {
        return false;
    }
    return true;
}

bool NgramEngine::buildIndex(
    size_t sectionOffset,
    size_t sectionEnd,
    uint32_t count,
    std::vector<IndexEntry>& index
) {
    if (sectionOffset > sectionEnd || sectionEnd > dataSize_ ||
        count > MAX_SECTION_ENTRIES) return false;
    const size_t sectionSize = sectionEnd - sectionOffset;
    if (count > sectionSize / 5U) return false;

    index.clear();
    index.reserve(count);
    size_t cursor = sectionOffset;
    uint32_t previousHash = 0;
    for (uint32_t item = 0; item < count; ++item) {
        if (sectionEnd - cursor < 5U) return false;
        const uint32_t keyHash = readLe32(data_ + cursor);
        const uint8_t resultCount = data_[cursor + 4];
        if (resultCount == 0 || resultCount > NGRM_MAX_RESULTS) return false;
        const size_t resultBytes = static_cast<size_t>(resultCount) * NGRM_RESULT_ENTRY_SIZE;
        if (resultBytes > sectionEnd - cursor - 5U) return false;
        if (item > 0 && keyHash < previousHash) return false;

        for (uint8_t result = 0; result < resultCount; ++result) {
            const size_t resultOffset = cursor + 5U +
                static_cast<size_t>(result) * NGRM_RESULT_ENTRY_SIZE;
            std::string ignored;
            if (!stringAt(readLe32(data_ + resultOffset), ignored)) return false;
        }

        index.push_back(IndexEntry{keyHash, data_ + cursor, resultCount});
        previousHash = keyHash;
        cursor += 5U + resultBytes;
    }
    return cursor == sectionEnd;
}

uint32_t NgramEngine::fnv1a(const uint8_t* bytes, size_t length) {
    uint32_t hash = 0x811c9dc5U;
    for (size_t index = 0; index < length; ++index) {
        hash ^= bytes[index];
        hash *= 0x01000193U;
    }
    return hash;
}

bool NgramEngine::normalizedKey(const char* input, std::string& output) {
    output.clear();
    if (input == nullptr) return false;
    const size_t length = strnlen(input, MAX_CONTEXT_BYTES + 1U);
    if (length == 0 || length > MAX_CONTEXT_BYTES) return false;
    output.assign(input, length);
    for (char& character : output) {
        if (character >= 'A' && character <= 'Z') {
            character = static_cast<char>(character + ('a' - 'A'));
        }
    }
    return true;
}

const NgramEngine::IndexEntry* NgramEngine::findEntry(
    uint32_t keyHash,
    const std::vector<IndexEntry>& index
) const {
    const auto found = std::lower_bound(
        index.begin(),
        index.end(),
        keyHash,
        [](const IndexEntry& entry, uint32_t value) { return entry.keyHash < value; }
    );
    return found != index.end() && found->keyHash == keyHash ? &*found : nullptr;
}

bool NgramEngine::stringAt(uint32_t offset, std::string& output) const {
    if (offset >= stringTableSize_) return false;
    const uint8_t* start = data_ + stringTableOffset_ + offset;
    const size_t remaining = stringTableSize_ - offset;
    const size_t searchLength = std::min(remaining, MAX_PREDICTION_BYTES + 1U);
    const void* terminator = std::memchr(start, '\0', searchLength);
    if (terminator == nullptr) return false;
    const auto* end = static_cast<const uint8_t*>(terminator);
    if (end == start) return false;
    if (!isValidUtf8(start, static_cast<size_t>(end - start))) return false;
    output.assign(reinterpret_cast<const char*>(start), static_cast<size_t>(end - start));
    return true;
}

std::vector<NgramResult> NgramEngine::parseResults(
    const IndexEntry* entry,
    size_t maxResults
) const {
    if (entry == nullptr || maxResults == 0) return {};
    const size_t limit = std::min<size_t>(entry->resultCount, maxResults);
    std::vector<NgramResult> results;
    results.reserve(limit);
    const uint8_t* resultPointer = entry->pointer + 5;
    for (size_t index = 0; index < limit; ++index) {
        std::string word;
        if (!stringAt(readLe32(resultPointer), word)) return {};
        results.push_back(NgramResult{std::move(word), readLe16(resultPointer + 4)});
        resultPointer += NGRM_RESULT_ENTRY_SIZE;
    }
    return results;
}

std::vector<NgramResult> NgramEngine::predictAfterWord(
    const char* word,
    size_t maxResults
) const {
    std::string normalized;
    if (!loaded_ || maxResults == 0 || !normalizedKey(word, normalized)) return {};
    return parseResults(
        findEntry(fnv1a(reinterpret_cast<const uint8_t*>(normalized.data()), normalized.size()),
                  bigramIndex_),
        maxResults
    );
}

std::vector<NgramResult> NgramEngine::predictAfterWords(
    const char* word1,
    const char* word2,
    size_t maxResults
) const {
    std::string normalized1;
    std::string normalized2;
    if (!loaded_ || maxResults == 0 || !normalizedKey(word1, normalized1) ||
        !normalizedKey(word2, normalized2)) {
        return {};
    }

    std::string trigramKey = normalized1;
    trigramKey.push_back('\0');
    trigramKey += normalized2;
    const auto* trigramEntry = findEntry(
        fnv1a(reinterpret_cast<const uint8_t*>(trigramKey.data()), trigramKey.size()),
        trigramIndex_
    );
    const auto* bigramEntry = findEntry(
        fnv1a(reinterpret_cast<const uint8_t*>(normalized2.data()), normalized2.size()),
        bigramIndex_
    );

    auto trigramResults = parseResults(trigramEntry, NGRM_MAX_RESULTS);
    auto bigramResults = parseResults(bigramEntry, NGRM_MAX_RESULTS);
    if (trigramResults.empty()) {
        for (auto& result : bigramResults) {
            result.score = static_cast<uint16_t>(
                static_cast<uint32_t>(result.score) * BACKOFF_NUMERATOR / BACKOFF_DENOMINATOR
            );
        }
        if (bigramResults.size() > maxResults) bigramResults.resize(maxResults);
        return bigramResults;
    }
    if (bigramResults.empty()) {
        if (trigramResults.size() > maxResults) trigramResults.resize(maxResults);
        return trigramResults;
    }

    std::vector<NgramResult> merged = trigramResults;
    for (auto& result : bigramResults) {
        const bool duplicate = std::any_of(
            trigramResults.begin(),
            trigramResults.end(),
            [&result](const NgramResult& candidate) { return candidate.word == result.word; }
        );
        if (!duplicate) {
            result.score = static_cast<uint16_t>(
                static_cast<uint32_t>(result.score) * BACKOFF_NUMERATOR / BACKOFF_DENOMINATOR
            );
            merged.push_back(std::move(result));
        }
    }
    std::stable_sort(
        merged.begin(),
        merged.end(),
        [](const NgramResult& left, const NgramResult& right) {
            return left.score > right.score;
        }
    );
    if (merged.size() > maxResults) merged.resize(maxResults);
    return merged;
}

uint16_t NgramEngine::bigramScore(const char* previousWord, const char* word) const {
    std::string normalizedPrevious;
    std::string normalizedWord;
    if (!loaded_ || !normalizedKey(previousWord, normalizedPrevious) ||
        !normalizedKey(word, normalizedWord)) {
        return 0;
    }
    const auto results = parseResults(
        findEntry(
            fnv1a(
                reinterpret_cast<const uint8_t*>(normalizedPrevious.data()),
                normalizedPrevious.size()
            ),
            bigramIndex_
        ),
        NGRM_MAX_RESULTS
    );
    const auto found = std::find_if(
        results.begin(),
        results.end(),
        [&normalizedWord](const NgramResult& result) { return result.word == normalizedWord; }
    );
    return found == results.end() ? 0 : found->score;
}

}  // namespace dictus
