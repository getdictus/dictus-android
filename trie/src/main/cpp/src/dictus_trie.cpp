// AOSP-inspired trie engine for Dictus. Apache 2.0.
#include "dictus_trie.h"
#include "dictus_trie_format.h"

#include <algorithm>
#include <cstring>
#include <fcntl.h>
#include <iterator>
#include <map>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unordered_set>
#include <utility>
#include <unistd.h>

namespace dictus {

Trie::Trie() : data_(nullptr), size_(0), fd_(-1) {}

Trie::~Trie() {
    unload();
}

bool Trie::loadMmap(const char* path) {
    unload();

    fd_ = open(path, O_RDONLY);
    if (fd_ < 0) return false;

    struct stat st;
    if (fstat(fd_, &st) != 0) {
        close(fd_);
        fd_ = -1;
        return false;
    }
    size_ = static_cast<size_t>(st.st_size);

    if (size_ < HEADER_SIZE) {
        close(fd_);
        fd_ = -1;
        size_ = 0;
        return false;
    }

    void* mapped = mmap(nullptr, size_, PROT_READ, MAP_PRIVATE, fd_, 0);
    if (mapped == MAP_FAILED) {
        close(fd_);
        fd_ = -1;
        size_ = 0;
        return false;
    }

    madvise(mapped, size_, MADV_RANDOM);
    data_ = static_cast<const uint8_t*>(mapped);

    // Validate the fixed header before parsing attacker-controlled offsets.
    DictusHeader header{};
    std::memcpy(&header, data_, sizeof(header));
    const auto* hdr = &header;
    if (std::memcmp(hdr->magic, DICTUS_MAGIC, 4) != 0) {
        unload();
        return false;
    }
    if (hdr->version != DICTUS_VERSION) {
        unload();
        return false;
    }

    if (hdr->flags != 0 || hdr->node_count == 0 || hdr->root_child_count == 0 ||
        hdr->node_count < hdr->root_child_count || !validateStructure()) {
        unload();
        return false;
    }

    return true;
}

void Trie::unload() {
    if (data_) {
        munmap(const_cast<uint8_t*>(data_), size_);
        data_ = nullptr;
    }
    if (fd_ >= 0) {
        close(fd_);
        fd_ = -1;
    }
    size_ = 0;
}

bool Trie::wordExists(const uint16_t* chars, int len) const {
    return traverse(chars, len) > 0;
}

uint16_t Trie::getFrequency(const uint16_t* chars, int len) const {
    return traverse(chars, len);
}

namespace {

uint32_t nodeSize(const TrieNode& node) {
    return node.byteSize;
}

struct Completion {
    std::u16string word;
    uint16_t frequency;
};

bool completionPrecedes(const Completion& left, const Completion& right) {
    if (left.frequency != right.frequency) return left.frequency > right.frequency;
    return left.word < right.word;
}

void addCompletion(std::vector<Completion>& results, Completion completion,
                   int maxResults) {
    const auto position = std::lower_bound(
        results.begin(), results.end(), completion, completionPrecedes);
    results.insert(position, std::move(completion));
    if (static_cast<int>(results.size()) > maxResults) results.pop_back();
}

void collectCompletions(const Trie& trie, const TrieNode& node,
                        std::u16string word, bool includeNode,
                        std::vector<Completion>& results, int maxResults,
                        uint32_t& visits) {
    if (++visits > Trie::MAX_COMPLETION_VISITS ||
        word.size() > static_cast<size_t>(Trie::MAX_WORD_CODE_UNITS)) return;
    if (includeNode && (node.flags & FLAG_TERMINAL)) {
        addCompletion(results, {word, node.frequency}, maxResults);
    }
    if (node.childCount == 0) return;

    uint32_t childOffset = node.childrenOffset;
    for (int index = 0; index < node.childCount; ++index) {
        const TrieNode child = trie.readNode(childOffset);
        if (!child.valid) return;
        std::u16string childWord = word;
        for (int charIndex = 0; charIndex < child.charCount; ++charIndex) {
            uint16_t character;
            std::memcpy(&character, &child.chars[charIndex], sizeof(character));
            childWord.push_back(static_cast<char16_t>(character));
        }
        collectCompletions(trie, child, std::move(childWord), true, results, maxResults,
                           visits);
        if (visits > Trie::MAX_COMPLETION_VISITS) return;
        childOffset += nodeSize(child);
    }
}

}  // namespace

std::vector<std::u16string> Trie::complete(const uint16_t* prefix, int len,
                                            int maxResults) const {
    std::vector<std::u16string> words;
    if (!data_ || prefix == nullptr || len <= 0 || maxResults <= 0) return words;

    uint32_t offset = HEADER_SIZE;
    int siblingCount = static_cast<int>(rootChildCount());
    int prefixPosition = 0;
    std::u16string word;
    std::vector<Completion> completions;
    uint32_t visits = 0;

    while (prefixPosition < len) {
        bool found = false;
        for (int sibling = 0; sibling < siblingCount; ++sibling) {
            const TrieNode node = readNode(offset);
            if (!node.valid) return words;

            const int remaining = len - prefixPosition;
            const int compared = std::min(remaining, node.charCount);
            bool matches = true;
            for (int index = 0; index < compared; ++index) {
                uint16_t character;
                std::memcpy(&character, &node.chars[index], sizeof(character));
                if (character != prefix[prefixPosition + index]) {
                    matches = false;
                    break;
                }
            }
            if (!matches) {
                offset += nodeSize(node);
                continue;
            }

            for (int index = 0; index < node.charCount; ++index) {
                uint16_t character;
                std::memcpy(&character, &node.chars[index], sizeof(character));
                word.push_back(static_cast<char16_t>(character));
            }
            prefixPosition += compared;
            if (prefixPosition == len) {
                // A prefix ending inside a Patricia node cannot equal that node's word.
                const bool includeNode = compared < node.charCount;
                collectCompletions(*this, node, std::move(word), includeNode,
                                   completions, maxResults, visits);
                for (const Completion& completion : completions) {
                    words.push_back(completion.word);
                }
                return words;
            }
            if (compared != node.charCount || node.childCount == 0) return words;
            offset = node.childrenOffset;
            siblingCount = node.childCount;
            found = true;
            break;
        }
        if (!found) return words;
    }
    return words;
}

const uint8_t* Trie::rootData() const {
    return data_ ? data_ + HEADER_SIZE : nullptr;
}

uint32_t Trie::maxFreq() const {
    if (!data_) return 0;
    return reinterpret_cast<const DictusHeader*>(data_)->max_freq;
}

uint32_t Trie::wordCount() const {
    if (!data_) return 0;
    return reinterpret_cast<const DictusHeader*>(data_)->word_count;
}

uint8_t Trie::rootChildCount() const {
    if (!data_) return 0;
    return reinterpret_cast<const DictusHeader*>(data_)->root_child_count;
}

size_t Trie::fileSize() const {
    return size_;
}

TrieNode Trie::readNode(uint32_t pos) const {
    TrieNode node;
    node.valid = false;
    node.flags = 0;
    node.chars = nullptr;
    node.charCount = 0;
    node.frequency = 0;
    node.childrenOffset = 0;
    node.childCount = 0;
    node.byteSize = 0;

    if (!data_ || pos < HEADER_SIZE || pos >= size_) return node;

    const uint8_t* p = data_ + pos;
    const uint8_t* end = data_ + size_;

    node.flags = *p++;
    if ((node.flags & 0x0F) != 0) return node;

    // Character count
    bool multiChar = (node.flags & FLAG_MULTI_CHAR) != 0;
    if (multiChar) {
        if (p >= end) return node;
        node.charCount = *p++;
        if (node.charCount < 2) return node;
    } else {
        node.charCount = 1;
    }

    // Characters (UTF-16 LE)
    const size_t charsBytes = static_cast<size_t>(node.charCount) * 2;
    if (charsBytes > static_cast<size_t>(end - p)) return node;
    node.chars = reinterpret_cast<const uint16_t*>(p);
    p += charsBytes;

    // Frequency (only if terminal)
    if (node.flags & FLAG_TERMINAL) {
        if (end - p < 2) return node;
        std::memcpy(&node.frequency, p, 2);
        p += 2;
    }

    // Children offset and count
    int ptrSize = childrenPtrSize(node.flags);
    if (ptrSize > 0) {
        if (p >= end) return node;
        node.childCount = *p++;
        if (node.childCount == 0) return node;

        if (end - p < ptrSize) return node;
        node.childrenOffset = 0;
        std::memcpy(&node.childrenOffset, p, ptrSize);
        p += ptrSize;
        if (node.childrenOffset < HEADER_SIZE || node.childrenOffset >= size_) return node;
    }

    node.byteSize = static_cast<uint32_t>(p - (data_ + pos));
    node.valid = node.byteSize > 0;
    return node;
}

bool Trie::validateStructure() const {
    if (!data_ || size_ > UINT32_MAX) return false;
    DictusHeader header{};
    std::memcpy(&header, data_, sizeof(header));
    std::unordered_set<uint32_t> starts;
    std::map<uint32_t, uint32_t> ranges;
    uint32_t terminals = 0;

    const auto validateList = [&](const auto& self, uint32_t offset, uint8_t count,
                                  uint32_t depth, uint32_t wordDepth) -> bool {
        if (count == 0 || depth > MAX_WORD_CODE_UNITS ||
            starts.size() >= header.node_count) return false;
        for (uint32_t index = 0; index < count; ++index) {
            const TrieNode node = readNode(offset);
            if (!node.valid || wordDepth + node.charCount > MAX_WORD_CODE_UNITS ||
                !starts.insert(offset).second) return false;
            const uint32_t end = offset + node.byteSize;
            if (end < offset || end > size_) return false;
            const auto next = ranges.lower_bound(offset);
            if ((next != ranges.end() && end > next->first) ||
                (next != ranges.begin() && std::prev(next)->second > offset)) return false;
            ranges.emplace(offset, end);
            if (node.flags & FLAG_TERMINAL) ++terminals;
            const int ptrSize = childrenPtrSize(node.flags);
            if ((ptrSize == 0) != (node.childCount == 0)) return false;
            if (node.childCount > 0 &&
                !self(self, node.childrenOffset, node.childCount, depth + 1,
                      wordDepth + node.charCount)) return false;
            offset = end;
        }
        return true;
    };

    if (!validateList(validateList, HEADER_SIZE, header.root_child_count, 1, 0)) return false;
    if (starts.size() != header.node_count || terminals != header.word_count) return false;
    uint32_t covered = HEADER_SIZE;
    for (const auto& range : ranges) {
        if (range.first != covered) return false;
        covered = range.second;
    }
    return covered == size_;
}

uint16_t Trie::traverse(const uint16_t* chars, int len) const {
    if (!data_ || len <= 0) return 0;

    // Root is virtual (not stored). Root child count is in header byte 20.
    // Children at each level are stored contiguously in the DFS layout.

    int inputPos = 0;
    uint32_t searchOffset = HEADER_SIZE;
    int siblingCount = static_cast<int>(rootChildCount());

    while (inputPos < len) {
        bool found = false;
        uint32_t offset = searchOffset;
        int siblingsChecked = 0;

        while (offset < size_ && siblingsChecked < siblingCount) {
            TrieNode node = readNode(offset);
            if (!node.valid) break;

            // Check if this node's characters match input at inputPos
            bool matches = true;
            int matchLen = node.charCount;
            if (inputPos + matchLen > len) {
                // Input is shorter than this node's chars -- no match
                matches = false;
            } else {
                for (int i = 0; i < matchLen; i++) {
                    uint16_t nc;
                    std::memcpy(&nc, &node.chars[i], 2);
                    if (nc != chars[inputPos + i]) {
                        matches = false;
                        break;
                    }
                }
            }

            if (matches) {
                inputPos += matchLen;
                if (inputPos == len) {
                    // Consumed all input -- check if terminal
                    return (node.flags & FLAG_TERMINAL) ? node.frequency : 0;
                }
                // Continue into children
                int ptrSize = childrenPtrSize(node.flags);
                if (ptrSize == 0 || node.childCount == 0) {
                    return 0; // no children but input remaining
                }
                searchOffset = node.childrenOffset;
                siblingCount = node.childCount;
                found = true;
                break;
            }

            // Advance to next sibling -- compute this node's byte size
            uint32_t nodeSize = 1; // flags
            if (node.flags & FLAG_MULTI_CHAR) nodeSize += 1; // char_count
            nodeSize += node.charCount * 2;
            if (node.flags & FLAG_TERMINAL) nodeSize += 2;
            int ptrSize = childrenPtrSize(node.flags);
            if (ptrSize > 0) nodeSize += 1 + ptrSize; // child_count + ptr
            offset += nodeSize;
            siblingsChecked++;
        }

        if (!found) return 0;
    }

    return 0;
}

} // namespace dictus
