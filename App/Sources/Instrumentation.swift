// Instrumentation.swift
// os_signpost intervals for measuring REAL keystroke handling latency per
// strategy (in-place / marked / tap-backspace / tap-selection / tap-emptyReset)
// — the milliseconds live in the IMKit/CGEvent round trips, not the engine.
//
// Signposts are buffered by the OS and near-free when no tool is recording, so
// they ship enabled in release builds.
//
// To record: Instruments → "os_signpost" instrument (or Logging template) →
// filter subsystem "com.viettelex.inputmethod.telex". Or from a terminal:
//   xcrun xctrace record --template 'Logging' --attach VietTelex --output /tmp/vt.trace
// Intervals:
//   imk.handle — one IMKit keystroke, message = strategy that handled it
//   tap.handle — one CGEventTap keystroke (terminal-class apps)
//   tap.emit   — one synthesized edit burst (backspaces+insert posted)
//   ax.replace — one Accessibility text edit (D1 selection-replace fast path)

import os
import Darwin
import Foundation

enum Signposts {
    static let poster = OSSignposter(subsystem: "com.viettelex.inputmethod.telex",
                                     category: "keystroke")

    /// Fault-level log for safety events (currently the cascade circuit breaker
    /// firing — Layer 3 in TerminalTap). Rare by construction, so a real log line
    /// (not just a signpost) is worth it: it's the breadcrumb that explains why
    /// Vietnamese-in-terminal went quiet after a synthetic-event storm was stopped.
    static let log = Logger(subsystem: "com.viettelex.inputmethod.telex",
                            category: "safety")
}

/// Process memory as Activity Monitor's "Memory" column reports it (phys_footprint:
/// dirty + compressed, incl. malloc pages that are freed but not yet returned).
/// One `task_info` call, ~1µs — never on the keystroke path.
enum MemoryFootprint {
    struct Sample { let current: UInt64; let peak: UInt64 }

    static func sample() -> Sample {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<natural_t>.size)
        let kr = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count)
            }
        }
        guard kr == KERN_SUCCESS else { return Sample(current: 0, peak: 0) }
        return Sample(current: info.phys_footprint, peak: UInt64(max(0, info.ledger_phys_footprint_peak)))
    }

    /// Hand freed malloc pages back to the OS after a transient peak (Settings window).
    /// ONE immediate call — the old code — runs before most of the SwiftUI/CoreAnimation
    /// graph is actually freed (teardown spans several runloop turns and CA commits), so
    /// those pages stayed counted forever: field 04/10/2026, 1.8.10 after opening Settings
    /// once = 54 MB, 17 MB of it free-but-held (49% fragmentation). Measured in the test
    /// host (MemoryFootprintTests, Debug, 04/10/2026): Settings closed → 120 MB after the
    /// one-hop pass, 69 MB after these delayed passes (malloc live 22 MB both). The one
    /// hop simply ran too early. Off main (utility QoS) so the zone walk never delays a keystroke;
    /// malloc is thread-safe. Calls superseded by a newer schedule are dropped.
    static let reliefDelays: [TimeInterval] = [0.5, 3, 10]
    private static let reliefLock = NSLock()
    nonisolated(unsafe) private static var reliefGeneration = 0

    static func relieveAfterTransientPeak(delays: [TimeInterval] = reliefDelays) {
        reliefLock.lock(); reliefGeneration &+= 1; let gen = reliefGeneration; reliefLock.unlock()
        for d in delays {
            DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + d) {
                reliefLock.lock(); let current = reliefGeneration == gen; reliefLock.unlock()
                guard current else { return }
                malloc_zone_pressure_relief(nil, 0)
            }
        }
    }

    static func megabytes(_ bytes: UInt64) -> String { String(format: "%.1f MB", Double(bytes) / 1_048_576) }
}
