(ns sepal.app.e2e.playwright
  "Thin Clojure wrapper around Playwright Java API for browser automation"
  (:import [com.microsoft.playwright Playwright BrowserType$LaunchOptions Page$WaitForSelectorOptions]
           [com.microsoft.playwright.options WaitForSelectorState]))

(defonce ^:dynamic *page* nil)

(defn- get-browser-type
  "Get browser type from environment variable, defaults to chromium"
  [playwright]
  (case (System/getenv "PLAYWRIGHT_BROWSER")
    "firefox" (.firefox playwright)
    "webkit" (.webkit playwright)
    (.chromium playwright))) ;; default to chromium

(defn start-browser!
  "Create Playwright instance, launch browser in headless mode, and return browser context.
   Browser type controlled by PLAYWRIGHT_BROWSER env var (chromium, firefox, webkit)."
  []
  (let [playwright (Playwright/create)
        browser-type (get-browser-type playwright)
        options (doto (BrowserType$LaunchOptions.)
                  (.setHeadless true))
        browser (.launch browser-type options)
        page (.newPage browser)]
    {:playwright playwright
     :browser browser
     :page page}))

(defn stop-browser!
  "Close browser and Playwright instance"
  [{:keys [browser playwright]}]
  (.close browser)
  (.close playwright))

(defmacro with-browser
  "Fixture macro for browser lifecycle. Binds *page* for test body."
  [& body]
  `(let [browser-ctx# (start-browser!)]
     (binding [*page* (:page browser-ctx#)]
       (try
         ~@body
         (finally
           (stop-browser! browser-ctx#))))))

(defn navigate
  "Navigate to URL and wait for load event"
  [url]
  (.navigate *page* url)
  ;; Wait for load event (all resources loaded including stylesheets and images)
  (.waitForLoadState *page*))

(defn wait-for-load-state
  "Wait for page to reach specified load state.
   States: 'load' (default), 'domcontentloaded', 'networkidle'"
  ([]
   (.waitForLoadState *page*))
  ([state]
   (.waitForLoadState *page* (case state
                               :load (com.microsoft.playwright.options.LoadState/LOAD)
                               :domcontentloaded (com.microsoft.playwright.options.LoadState/DOMCONTENTLOADED)
                               :networkidle (com.microsoft.playwright.options.LoadState/NETWORKIDLE)))))

(defn click
  "Click element by selector"
  [selector]
  (.click *page* selector))

(defn press
  "Press a key. If one arg, presses on 'body'. If two, presses on selector."
  ([key]
   (.press *page* "body" key))
  ([selector key]
   (.press *page* selector key)))

(defn fill
  "Fill input field with value and dispatch input event for Alpine.js"
  [selector value]
  (.fill *page* selector value)
  ;; Dispatch input event to ensure Alpine.js form-state picks up the change
  (.dispatchEvent (.locator *page* selector)
                  "input"
                  (java.util.HashMap.)))

(defn select-option
  "Select option(s) in a dropdown by value"
  [selector value]
  (.selectOption *page* selector value))

(defn text-content
  "Get text content of element"
  [selector]
  (.textContent *page* selector))

(defn visible?
  "Check if element is visible"
  [selector]
  (.isVisible *page* selector))

(defn wait-for-selector
  "Wait for element to appear (with optional timeout in ms)"
  ([selector]
   (.waitForSelector *page* selector))
  ([selector timeout-ms]
   (.waitForSelector *page* selector
                     (doto (Page$WaitForSelectorOptions.)
                       (.setTimeout (double timeout-ms))))))

(defn wait-for-url
  "Wait for URL to match pattern (regex or string), with optional timeout in ms"
  ([pattern]
   (wait-for-url pattern 30000))
  ([pattern timeout-ms]
   (let [options (doto (com.microsoft.playwright.Page$WaitForURLOptions.)
                   (.setTimeout (double timeout-ms)))]
     (if (string? pattern)
       (.waitForURL *page* pattern options)
       (.waitForURL *page* (re-pattern pattern) options)))))

(defn wait-for-hidden
  "Wait for element to be hidden/detached (with optional timeout in ms)"
  ([selector]
   (.waitForSelector *page* selector
                     (doto (Page$WaitForSelectorOptions.)
                       (.setState WaitForSelectorState/HIDDEN))))
  ([selector timeout-ms]
   (.waitForSelector *page* selector
                     (doto (Page$WaitForSelectorOptions.)
                       (.setState WaitForSelectorState/HIDDEN)
                       (.setTimeout (double timeout-ms))))))

(defn wait-for-attached
  "Wait for element to be attached to the DOM (even if hidden)"
  ([selector]
   (.waitForSelector *page* selector
                     (doto (Page$WaitForSelectorOptions.)
                       (.setState WaitForSelectorState/ATTACHED))))
  ([selector timeout-ms]
   (.waitForSelector *page* selector
                     (doto (Page$WaitForSelectorOptions.)
                       (.setState WaitForSelectorState/ATTACHED)
                       (.setTimeout (double timeout-ms))))))

(defn get-url
  "Get current page URL"
  []
  (.url *page*))

(defn wait-for-enabled
  "Wait for element to be enabled (not disabled).
   Uses Playwright's Locator API to support Playwright selectors like :has-text()."
  ([selector]
   (wait-for-enabled selector 5000))
  ([selector timeout-ms]
   (let [locator (.locator *page* selector)
         start-time (System/currentTimeMillis)
         poll-interval 100]
     (loop []
       (if (.isEnabled locator)
         true
         (if (> (- (System/currentTimeMillis) start-time) timeout-ms)
           (throw (ex-info (str "Timeout waiting for element to be enabled: " selector)
                           {:selector selector :timeout-ms timeout-ms}))
           (do
             (Thread/sleep poll-interval)
             (recur))))))))