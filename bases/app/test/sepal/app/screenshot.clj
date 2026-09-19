(ns sepal.app.screenshot
  "Capture the accessions list at three viewport widths for the marketing site.

  Boots a real server on a seeded demo database and drives chromium over the
  same Playwright wrapper the e2e suite uses. The widths straddle the app's
  breakpoints at 639, 767 and 1023 px, so each capture is a different layout
  rather than the same one scaled.

  Writes `accessions-<viewport>.webp`, the names and format the marketing
  site's `static/screens/` holds, so OUT_DIR can point straight at it:

  clojure -M:dev:test:test-e2e -m sepal.app.screenshot ../marketing/static/screens"
  (:require [babashka.fs :as fs]
            [clojure.java.shell :as shell]
            [sepal.accession.interface :as acc.i]
            [sepal.app.e2e.server :as server]
            [sepal.location.interface :as loc.i]
            [sepal.material.interface :as mat.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i])
  (:import [com.microsoft.playwright Browser$NewContextOptions BrowserType$LaunchOptions
            Page$ScreenshotOptions Playwright]
           [com.microsoft.playwright.options Cookie]
           [java.nio.file Paths]))

(def ^:private email "curator@example.org")
(def ^:private password "DemoPassword123!")

(def viewports
  "Name, width and height per capture. Each width sits in a different band of
  the app's breakpoints: phone below 639, tablet between 768 and 1023, desktop
  above.

  `rail-open?` writes the `spl-rail` cookie the shell reads on first paint, so
  the section rail shows its labels. Only desktop sets it: below 1024px the
  rail is off-canvas and an open one covers the list."
  [{:name "desktop" :width 1440 :height 900 :rail-open? true}
   {:name "tablet" :width 834 :height 1112}
   {:name "phone" :width 390 :height 844}])

(def ^:private locations
  [{:code "GH1" :name "Orchid House"}
   {:code "SH2" :name "Shade House"}
   {:code "ARB" :name "Arboretum"}
   {:code "PW" :name "Palm Walk"}
   {:code "NB3" :name "Nursery Bed 3"}])

(def ^:private accessions
  "One row per accession in the list, with the taxon it is determined as. The
  list shows code, taxon, provenance and date received, so those are the fields
  that have to be right."
  [{:code "2019.0031" :taxon "Swietenia macrophylla" :author "King"
    :provenance :wild :received "2019-03-14" :location "ARB" :quantity 3}
   {:code "2019.0147" :taxon "Cedrela odorata" :author "L."
    :provenance :wild :received "2019-07-02" :location "ARB" :quantity 2}
   {:code "2020.0008" :taxon "Heliconia rostrata" :author "Ruiz & Pav."
    :provenance :cultivated :received "2020-01-19" :location "SH2" :quantity 12}
   {:code "2020.0094" :taxon "Theobroma cacao" :author "L."
    :provenance :cultivated :received "2020-05-27" :location "NB3" :quantity 8}
   {:code "2020.0211" :taxon "Prosthechea cochleata" :author "(L.) W.E.Higgins"
    :provenance :wild :received "2020-09-08" :location "GH1" :quantity 4}
   {:code "2021.0019" :taxon "Bactris gasipaes" :author "Kunth"
    :provenance :purchase :received "2021-02-11" :location "PW" :quantity 6}
   {:code "2021.0076" :taxon "Vanilla planifolia" :author "Andrews"
    :provenance :cultivated :received "2021-04-30" :location "GH1" :quantity 5}
   {:code "2021.0182" :taxon "Plumeria rubra" :author "L."
    :provenance :cultivated :received "2021-08-16" :location "ARB" :quantity 2}
   {:code "2022.0044" :taxon "Cattleya skinneri" :author "Bateman"
    :provenance :wild :received "2022-03-03" :location "GH1" :quantity 7}
   {:code "2022.0130" :taxon "Coccoloba uvifera" :author "(L.) L."
    :provenance :wild :received "2022-06-21" :location "PW" :quantity 4}
   {:code "2022.0198" :taxon "Anthurium schlechtendalii" :author "Kunth"
    :provenance :wild :received "2022-10-05" :location "SH2" :quantity 9}
   {:code "2023.0022" :taxon "Philodendron hederaceum" :author "(Jacq.) Schott"
    :provenance :cultivated :received "2023-01-28" :location "SH2" :quantity 14}
   {:code "2023.0091" :taxon "Chamaedorea elegans" :author "Mart."
    :provenance :purchase :received "2023-05-12" :location "PW" :quantity 11}
   {:code "2023.0165" :taxon "Costus woodsonii" :author "Maas"
    :provenance :cultivated :received "2023-09-01" :location "NB3" :quantity 6}
   {:code "2024.0013" :taxon "Ceiba pentandra" :author "(L.) Gaertn."
    :provenance :wild :received "2024-01-15" :location "ARB" :quantity 1}
   {:code "2024.0057" :taxon "Cordia alliodora" :author "(Ruiz & Pav.) Oken"
    :provenance :wild :received "2024-02-26" :location "ARB" :quantity 5}
   {:code "2024.0104" :taxon "Monstera deliciosa" :author "Liebm."
    :provenance :cultivated :received "2024-04-08" :location "SH2" :quantity 10}
   {:code "2024.0139" :taxon "Aechmea bracteata" :author "(Sw.) Griseb."
    :provenance :wild :received "2024-05-20" :location "GH1" :quantity 8}
   {:code "2024.0186" :taxon "Guaiacum sanctum" :author "L."
    :provenance :wild :received "2024-07-11" :location "ARB" :quantity 3}
   {:code "2024.0221" :taxon "Tillandsia streptophylla" :author "Scheidw. ex C.Morren"
    :provenance :wild :received "2024-09-02" :location "GH1" :quantity 15}
   {:code "2025.0018" :taxon "Calophyllum brasiliense" :author "Cambess."
    :provenance :cultivated :received "2025-01-23" :location "ARB" :quantity 4}
   {:code "2025.0062" :taxon "Zamia prasina" :author "W.Bull"
    :provenance :wild :received "2025-03-17" :location "SH2" :quantity 6}
   {:code "2025.0115" :taxon "Dypsis lutescens" :author "(H.Wendl.) Beentje & J.Dransf."
    :provenance :purchase :received "2025-05-29" :location "PW" :quantity 9}
   {:code "2025.0178" :taxon "Talipariti tiliaceum" :author "(L.) Fryxell"
    :provenance :cultivated :received "2025-08-14" :location "PW" :quantity 7}])

(defn- check!
  "Throw on an error map rather than letting a bad row surface as a blank cell."
  [what result]
  (if (:error result)
    (throw (ex-info (str "Could not create " what) {:result result}))
    result))

(defn seed!
  "Write the demo collection and return nothing useful. Runs once per boot
  against a fresh temporary database, so it never has to be idempotent."
  [db]
  (user.i/create! db {:email email :password password :role :admin})
  (let [loc-ids (into {}
                      (for [{:keys [code name]} locations]
                        [code (:location/id (check! "location"
                                                    (loc.i/create! db {:code code :name name})))]))]
    (doseq [{:keys [code taxon author provenance received location quantity]} accessions]
      (let [t (check! "taxon" (taxon.i/create! db {:name taxon
                                                   :author author
                                                   :rank "species"}))
            a (check! "accession" (acc.i/create! db {:code code
                                                     :taxon-id (:taxon/id t)
                                                     :provenance-type provenance
                                                     :date-received received}))]
        (check! "material" (mat.i/create! db {:code (str code ".1")
                                              :accession-id (:accession/id a)
                                              :location-id (get loc-ids location)
                                              :type :plant
                                              :status :alive
                                              :quantity quantity}))))))

(defn- webp!
  "Encode png-path to webp-path with cwebp and delete the PNG.

  Playwright writes PNG only. At 2x the three captures are 612 KB as PNG and
  280 KB as WebP, and the marketing site commits them rather than building
  them, so the format is the only lever on their weight. cwebp comes from
  libwebp in devenv.nix."
  [png-path webp-path]
  (let [{:keys [exit err]} (shell/sh "cwebp" "-quiet" "-q" "82" "-m" "6"
                                     png-path "-o" webp-path)]
    (when-not (zero? exit)
      (throw (ex-info "cwebp failed" {:exit exit :err err :png png-path})))
    (fs/delete png-path)))

(defn- capture!
  "Log in at one viewport and write the accessions list to out-dir, named the
  way the marketing site's static/screens/ expects."
  [browser base-url out-dir {:keys [name width height rail-open?]}]
  (let [ctx (.newContext browser (doto (Browser$NewContextOptions.)
                                   (.setViewportSize (int width) (int height))
                                   (.setDeviceScaleFactor 2.0)))
        page (.newPage ctx)
        path (str out-dir "/accessions-" name ".png")
        webp (str out-dir "/accessions-" name ".webp")]
    (try
      (when rail-open?
        (.addCookies ctx [(doto (Cookie. "spl-rail" "1")
                            (.setUrl base-url))]))
      (.navigate page (str base-url "/login"))
      (.waitForSelector page "input[name=\"email\"]")
      (.fill page "input[name=\"email\"]" email)
      (.fill page "input[name=\"password\"]" password)
      (.click page "button:has-text(\"Login\")")
      (.waitForURL page (re-pattern "/activity"))
      (.navigate page (str base-url "/accession/"))
      (.waitForSelector page "text=2025.0178")
      ;; The list loads rows over htmx, so the table is in the DOM before it has
      ;; settled at its final width. Without this the tablet capture catches a
      ;; half-collapsed table.
      (.waitForLoadState page (com.microsoft.playwright.options.LoadState/NETWORKIDLE))
      (.screenshot page (doto (Page$ScreenshotOptions.)
                          (.setPath (Paths/get path (into-array String [])))))
      (webp! path webp)
      (println "wrote" webp (str "(" width "x" height " @2x)"))
      (finally
        (.close ctx)))))

(defn -main
  [& args]
  (let [out-dir (or (first args) "/tmp/sepal-screenshots")
        started (server/start-server!)]
    (.mkdirs (java.io.File. out-dir))
    (try
      (seed! (server/db started))
      (let [pw (Playwright/create)
            browser (.launch (.chromium pw)
                             (doto (BrowserType$LaunchOptions.) (.setHeadless true)))]
        (try
          (doseq [viewport viewports]
            (capture! browser (server/server-url started) out-dir viewport))
          (finally
            (.close browser)
            (.close pw))))
      (finally
        (server/stop-server! started)))
    (shutdown-agents)))
