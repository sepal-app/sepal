(ns sepal.app.integration.playwright
  "Thin Clojure wrapper around Playwright Java API for browser automation"
  (:import [com.microsoft.playwright Playwright Browser Page
            BrowserType$LaunchOptions Page$WaitForSelectorOptions Page$WaitForLoadStateOptions]))

(defonce ^:dynamic *page* nil)

(defn- get-browser-type
  "Get browser type from environment variable, defaults to chromium"
  [playwright]
  (case (System/getenv "PLAYWRIGHT_BROWSER")
    "firefox" (.firefox playwright)
    "webkit" (.webkit playwright)
    (.chromium playwright)))  ;; default to chromium

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

(defn click
  "Click element by selector"
  [selector]
  (.click *page* selector))

(defn fill
  "Fill input field with value"
  [selector value]
  (.fill *page* selector value))

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
  "Wait for URL to match pattern (regex or string)"
  [pattern]
  (if (string? pattern)
    (.waitForURL *page* pattern)
    (.waitForURL *page* (re-pattern pattern))))

(defn get-url
  "Get current page URL"
  []
  (.url *page*))
