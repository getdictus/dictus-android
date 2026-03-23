@file:Suppress("unused")

package dev.pivisolutions.dictus.service

/**
 * Type alias for backward compatibility.
 *
 * DictationState has been moved to the core module (dev.pivisolutions.dictus.core.service)
 * so both app and ime modules can reference it without circular dependencies.
 */
typealias DictationState = dev.pivisolutions.dictus.core.service.DictationState
