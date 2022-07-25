import * as uuid from 'uuid'
export * from './BoundsUtils'
export * from './PointUtils'
export * from './KeyUtils'
export * from './GeomUtils'
export * from './PolygonUtils'
export * from './SvgPathUtils'
export * from './DataUtils'
export * from './TextUtils'

export function uniqueId() {
  return uuid.v1()
}

export function validUUID(input: string) {
  try {
    uuid.parse(input)
    return true
  } catch {
    return false
  }
}

// via https://github.com/bameyrick/throttle-typescript
export function throttle<T extends (...args: any) => any>(
  func: T,
  limit: number
): (...args: Parameters<T>) => ReturnType<T> {
  let inThrottle: boolean
  let lastResult: ReturnType<T>

  return function (this: any, ...args: any[]): ReturnType<T> {
    if (!inThrottle) {
      inThrottle = true

      setTimeout(() => (inThrottle = false), limit)

      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-ignore
      lastResult = func(...args)
    }

    return lastResult
  }
}

export function debounce<T extends (...args: any[]) => void>(fn: T, ms = 0) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let timeoutId: number | any
  return function (...args: Parameters<T>) {
    clearTimeout(timeoutId)
    timeoutId = setTimeout(() => fn.apply(args), ms)
  }
}

/** Linear interpolate between two values. */
export function lerp(a: number, b: number, t: number) {
  return a + (b - a) * t
}

/** Find whether the current device is a Mac / iOS / iPadOS. */
export function isDarwin(): boolean {
  return /Mac|iPod|iPhone|iPad/.test(window.navigator.platform)
}

/**
 * Get whether an event is command (mac) or control (pc).
 *
 * @param e
 */
export function modKey(e: any): boolean {
  return isDarwin() ? e.metaKey : e.ctrlKey
}