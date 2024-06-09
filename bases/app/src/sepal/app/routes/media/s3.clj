(ns sepal.app.routes.media.s3
  (:require [babashka.fs :as fs]
            [camel-snake-kebab.core :as csk]
            [clojure.string :as s]
            [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.router :refer [url-for]]
            [sepal.app.ui.form :as form]
            [sepal.aws-s3.interface :as aws-s3.i])
  (:import [java.security SecureRandom]))

(defn random-hex [length]
  (let [ba (byte-array (int (/ length 2)))]
    (doto (SecureRandom.)
      (.nextBytes ba))
    (.toString (BigInteger. 1 ba) 16)))

(defn success-form [& {:keys [fields router]}]
  [:form {:id (s/replace (:id fields) #"\/" "_")
          :hx-post (url-for router :media/uploaded)
          :hx-target "#media-list"
          :hx-swap "afterbegin"}
   (form/anti-forgery-field)
   (into [:<>] (for [[key value] fields]
                 (form/hidden-field :name  (csk/->camelCaseString key)
                                    :value value)))])

(defn handler [& {:keys [context params ::r/router] :as _request}]
  (let [{:keys [s3-presigner media-upload-bucket]} context
        {files :files
         organization-id :organizationId} params
        s3-key-fn (fn [filename]
                    (format "organization_id=%s/%s.%s"
                            organization-id
                            (random-hex 20)
                            (fs/extension filename)))
        presign-fn (fn [file]
                     (aws-s3.i/presign-put-url media-upload-bucket
                                               (:s3-key file)
                                               (:content-type file)
                                               :presigner s3-presigner))]

    ;; This will render a form that will submit to /media/uploaded which will
    ;; will create the uploaded media item in the database and the result of
    ;; that form will be inserted into the media by htmx.
    (->> files
         (mapv #(json/parse-str % {:key-fn csk/->kebab-case-keyword}))
         (mapv #(merge % {:organization-id organization-id
                          :s3-bucket media-upload-bucket
                          ;; :s3-url (presign-fn %)
                          :s3-key (s3-key-fn (:filename %))
                          :s3-method "PUT"}))
         (mapv #(assoc % :s3-url (presign-fn %)))
         (mapv #(success-form :router router :fields %))
         (into [:<>])
         (html/render-partial))))
