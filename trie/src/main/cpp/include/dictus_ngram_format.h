// N-gram binary format for Dictus next-word prediction. Apache 2.0.
#ifndef DICTUS_NGRAM_FORMAT_H
#define DICTUS_NGRAM_FORMAT_H

#include <cstdint>

namespace dictus {

static constexpr char NGRM_MAGIC[4] = {'N', 'G', 'R', 'M'};
static constexpr uint16_t NGRM_VERSION = 1;
static constexpr uint8_t NGRM_MAX_RESULTS = 16;
static constexpr uint32_t NGRM_HEADER_SIZE = 32;
static constexpr uint32_t NGRM_RESULT_ENTRY_SIZE = 6;

}  // namespace dictus

#endif  // DICTUS_NGRAM_FORMAT_H
