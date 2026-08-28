// N-gram prediction engine for Dictus. Apache 2.0.
#ifndef DICTUS_NGRAM_H
#define DICTUS_NGRAM_H

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace dictus {

struct NgramResult {
    std::string word;
    uint16_t score;
};

/** Read-only mmap-backed NGRM v1 reader. Context identity follows the format's hash semantics. */
class NgramEngine {
public:
    NgramEngine();
    ~NgramEngine();

    NgramEngine(const NgramEngine&) = delete;
    NgramEngine& operator=(const NgramEngine&) = delete;

    /** The regular file must remain immutable until unload; production installs it atomically. */
    bool load(const char* path);
    void unload();
    bool isLoaded() const;

    std::vector<NgramResult> predictAfterWord(const char* word, size_t maxResults) const;
    std::vector<NgramResult> predictAfterWords(
        const char* word1,
        const char* word2,
        size_t maxResults
    ) const;
    uint16_t bigramScore(const char* previousWord, const char* word) const;

private:
    struct IndexEntry {
        uint32_t keyHash;
        const uint8_t* pointer;
        uint8_t resultCount;
    };

    uint8_t* data_;
    size_t dataSize_;
    size_t stringTableOffset_;
    size_t stringTableSize_;
    bool loaded_;
    std::vector<IndexEntry> bigramIndex_;
    std::vector<IndexEntry> trigramIndex_;

    bool validateAndBuildIndices();
    bool buildIndex(
        size_t sectionOffset,
        size_t sectionEnd,
        uint32_t count,
        std::vector<IndexEntry>& index
    );
    static uint16_t readLe16(const uint8_t* pointer);
    static uint32_t readLe32(const uint8_t* pointer);
    static uint32_t fnv1a(const uint8_t* bytes, size_t length);
    static bool normalizedKey(const char* input, std::string& output);
    const IndexEntry* findEntry(uint32_t keyHash, const std::vector<IndexEntry>& index) const;
    bool stringAt(uint32_t offset, std::string& output) const;
    std::vector<NgramResult> parseResults(const IndexEntry* entry, size_t maxResults) const;
};

}  // namespace dictus

#endif  // DICTUS_NGRAM_H
