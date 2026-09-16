import { Option } from "slim-select"

/** What a picker endpoint answers: the options it can offer, and how many
 *  records matched in all. */
export interface PickerResponse {
    options: Array<Record<string, unknown>>
    total: number
}

/**
 * How many options a picker asks for.
 *
 * High, because anything past it is not merely unseen but unpickable: the
 * dropdown does not page, so the only way to reach a record it cut is to type
 * something that narrows to it — and when you are choosing rather than
 * recalling, you do not know what that would be. A garden holds tens of
 * locations and contacts, so at this size those lists are never cut at all.
 *
 * It costs nothing. The sort has already run over every matching row by the
 * time the limit applies: measured against 452k taxa, a broad query took
 * 4.7ms at 100 and 5.6ms at 10, and 100 taxa are about 10KB of JSON.
 *
 * `truncationNotice` covers what is left — a taxonomy this cannot browse.
 */
export const PAGE_SIZE = 100

/**
 * A final row saying the list was cut short, or nothing when it was not.
 *
 * Disabled, so it cannot be chosen and SlimSelect renders it greyed as a
 * label rather than an option.
 */
export function truncationNotice(shown: number, total: number): Option[] {
    if (!Number.isFinite(total) || total <= shown) return []
    return [
        {
            text: `Showing ${shown} of ${total} — keep typing to narrow`,
            value: "__truncated__",
            disabled: true,
        } as Option,
    ]
}
