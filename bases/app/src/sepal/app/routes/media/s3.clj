(ns sepal.app.routes.media.s3
  (:require [babashka.fs :as fs]
            [camel-snake-kebab.core :as csk]
            [clojure.string :as s]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.params :as params]
            [sepal.app.routes.media.routes :as media.routes]
            [sepal.app.ui.form :as form]
            [sepal.aws-s3.interface :as aws-s3.i]
            [zodiac.core :as z])
  (:import [java.security SecureRandom]))

(defn random-hex [length]
  (let [ba (byte-array (int (/ length 2)))]
    (doto (SecureRandom.)
      (.nextBytes ba))
    (.toString (BigInteger. 1 ba) 16)))

(defn success-form [& {:keys [fields]}]
  [:form {:id (s/replace (:id fields) #"\/" "_")
          :hx-post (z/url-for media.routes/uploaded)
          :hx-target "#media-list"
          :hx-swap "afterbegin"}
   (form/anti-forgery-field)
   (for [[key value] fields]
     (form/hidden-field :name  (csk/->camelCaseString key)
                        :value value))])

(def FormParams
  [:map {:closed true}
   [:files [:or
            :string
            [:vector :string]]]
   ;; TODO: Validate that if we have one then we require both if
   ;; linkResourceType/Id
   [:linkResourceType [:maybe :string]]
   [:linkResourceId [:maybe :string]]])

(defn handler [& {:keys [::z/context form-params] :as _request}]
  (let [{:keys [s3-presigner media-upload-bucket media-key-prefix]} context
        {files :files
         link-resource-type :linkResourceType
         link-resource-id :linkResourceId
         :as params} (params/decode FormParams form-params)
        files (if (sequential? files) files [files])
        s3-key-fn (fn [filename]
                    (format "%s%s.%s"
                            media-key-prefix
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
         ;; Lowercase content-type to match presigned URL signature
         (mapv #(update % :content-type s/lower-case))
         (mapv #(merge % {:link-resource-type link-resource-type
                          :link-resource-id link-resource-id
                          :s3-bucket media-upload-bucket
                          ;; :s3-url (presign-fn %)
                          :s3-key (s3-key-fn (:filename %))
                          :s3-method "PUT"}))
         (mapv #(assoc % :s3-url (presign-fn %)))
         (mapv #(success-form :fields %))
         (html/render-partial))))
