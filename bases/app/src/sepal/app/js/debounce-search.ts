/**
 * Hold a SlimSelect search until the typing stops.
 *
 * SlimSelect raises `search` on every keystroke, so typing a name sent one
 * request per letter — and the short prefixes are the expensive ones to
 * answer, because they match the most rows. A burst of keystrokes now costs a
 * single query, the one for what was actually typed.
 *
 * A superseded call is left unsettled rather than rejected. SlimSelect renders
 * a rejection as a message in the dropdown, and "cancelled" is not something to
 * show a reader who is still typing. Only the last call of a burst settles,
 * which is the one whose results are wanted.
 */
export function debounceSearch<A extends unknown[], T>(
    fn: (...args: A) => Promise<T>,
    waitMs = 250,
): (...args: A) => Promise<T> {
    let timer: ReturnType<typeof setTimeout> | undefined
    return (...args: A) =>
        new Promise<T>((resolve, reject) => {
            if (timer !== undefined) clearTimeout(timer)
            timer = setTimeout(() => {
                timer = undefined
                fn(...args).then(resolve, reject)
            }, waitMs)
        })
}
