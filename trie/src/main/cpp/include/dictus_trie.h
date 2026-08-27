// AOSP-inspired trie engine for Dictus. Apache 2.0.
#ifndef DICTUS_TRIE_H
#define DICTUS_TRIE_H

#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>

namespace dictus {

/// Parsed trie node from binary data.
struct TrieNode {
    bool valid;
    uint8_t flags;
    const uint16_t* chars;  // pointer into mmap'd data
    int charCount;
    uint16_t frequency;     // 0 if not terminal
    uint32_t childrenOffset;// absolute offset to first child
    uint8_t childCount;     // number of direct children
    uint32_t byteSize;      // validated encoded size of this node
};

/// Read-only trie loaded via mmap from a .dict file.
class Trie {
public:
    Trie();
    ~Trie();

    // Non-copyable
    Trie(const Trie&) = delete;
    Trie& operator=(const Trie&) = delete;

    /// Load dictionary file via mmap. Returns true on success.
    bool loadMmap(const char* path);

    /// Unload and release resources.
    void unload();

    /// Check if a word exists in the trie.
    /// chars: UTF-16 code units, len: number of code units.
    bool wordExists(const uint16_t* chars, int len) const;

    /// Get frequency of a word (0 if not found).
    uint16_t getFrequency(const uint16_t* chars, int len) const;

    /// Return the highest-frequency words beginning with prefix. The exact
    /// prefix is excluded. Frequency ties are ordered lexicographically.
    std::vector<std::u16string> complete(const uint16_t* prefix, int len,
                                         int maxResults) const;

    static constexpr uint32_t MAX_COMPLETION_VISITS = 20000;
    static constexpr uint32_t MAX_FUZZY_STATES = 100000;
    static constexpr int MAX_WORD_CODE_UNITS = 127;

    /// Pointer to first node data (past header).
    const uint8_t* rootData() const;

    /// Max frequency stored in header.
    uint32_t maxFreq() const;

    /// Word count from header.
    uint32_t wordCount() const;

    /// Number of root-level children (top-level siblings in trie).
    uint8_t rootChildCount() const;

    /// Total file size.
    size_t fileSize() const;

    /// Read a node at the given absolute byte offset.
    TrieNode readNode(uint32_t pos) const;

private:
    const uint8_t* data_;
    size_t size_;
    int fd_;

    bool validateStructure() const;

    /// Traverse trie matching chars. Returns terminal node frequency, or 0.
    uint16_t traverse(const uint16_t* chars, int len) const;
};

} // namespace dictus

#endif // DICTUS_TRIE_H
