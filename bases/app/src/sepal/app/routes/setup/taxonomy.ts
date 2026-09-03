/**
 * Live progress for the setup wizard's taxonomy import.
 *
 * The two downloads are 35 MB and ~127 MB and run on a background thread, so
 * the page cannot learn anything from the response that started them. It opens
 * an EventSource instead and lets the server push byte counts as they arrive.
 *
 * Usage, on the element whose x-data holds the initial frame:
 *
 *   <div x-data='{"phase":"downloading-taxa", ...}'
 *        x-setup-progress="/setup/taxonomy/progress"
 *        data-done-url="/setup/review">
 *
 * Registered by setup.ts rather than by a script tag on the taxonomy page. The
 * wizard's body carries hx-boost, so the POST that starts the import arrives as
 * a swap long after alpine:init has fired — a page-specific script would add a
 * listener for an event that never comes again and the directive would never
 * be registered on the one path that needs it.
 */

import Alpine, { type DirectiveCallback } from "alpinejs"

type ProgressFrame = {
    phase: string
    bytesDone: number | null
    bytesTotal: number | null
    percent: number | null
    approximate: boolean
    wfoVersion: string | null
    taxaCount: number | null
    synonyms: boolean
    error: string | null
    warning: string | null
}

const TERMINAL = ["done", "failed"]

const SetupProgress: DirectiveCallback = (el, { expression }, { cleanup }) => {
    const source = new EventSource(expression)

    // Closed on a terminal phase and on teardown alike. An EventSource left
    // open reconnects forever, and the server holds a thread per connection.
    let closed = false
    const close = () => {
        if (!closed) {
            closed = true
            source.close()
        }
    }

    source.addEventListener("message", event => {
        let frame: ProgressFrame
        try {
            frame = JSON.parse(event.data)
        } catch {
            return
        }

        // Object.keys rather than Object.entries: tsconfig targets es2016.
        const state = Alpine.$data(el) as unknown as Record<string, unknown>
        const incoming = frame as unknown as Record<string, unknown>
        for (const key of Object.keys(incoming)) {
            state[key] = incoming[key]
        }

        if (TERMINAL.includes(frame.phase)) {
            close()
            if (frame.phase === "done") {
                const next = el.getAttribute("data-done-url")
                if (next) {
                    window.location.assign(next)
                }
            }
        }
    })

    // An error before the first frame is a transient reconnect, which
    // EventSource handles on its own. One after the stream closed is the close
    // itself, and reconnecting would reopen a finished job.
    source.addEventListener("error", () => {
        if (source.readyState === EventSource.CLOSED) {
            close()
        }
    })

    cleanup(close)
}

export default SetupProgress
