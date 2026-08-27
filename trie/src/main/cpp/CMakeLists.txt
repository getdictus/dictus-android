cmake_minimum_required(VERSION 3.22.1)
project(dictus_trie LANGUAGES CXX)

add_library(
    dictus_trie
    SHARED
    src/dictus_trie.cpp
    src/dictus_scorer.cpp
    src/dictus_proximity.cpp
    dictus_trie_jni.cpp
)

target_include_directories(dictus_trie PRIVATE include)
target_compile_features(dictus_trie PRIVATE cxx_std_17)
target_compile_options(dictus_trie PRIVATE -O3 -Wall -Wextra -Werror)