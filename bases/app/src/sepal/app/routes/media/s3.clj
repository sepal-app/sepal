(ns sepal.app.routes.media.s3
  (:require [babashka.fs :as fs]
            [camel-snake-kebab.core :as csk]
            [clojure.string :as s]
            [reitit.core :as r]
            [sepal.app.html :as html]
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
  [:form {:id (s/replace (:file-id fields) #"\/" "_")
          :hx-post (url-for router :media/uploaded)
          :hx-target "#media-list"
          :hx-swap "afterbegin"}
   (form/anti-forgery-field)
   (into [:<>] (for [[key value] fields]
                 (form/hidden-field :name  (csk/->camelCaseString key)
                                    :value value)))])

(defn handler [& {:keys [context params ::r/router] :as _request}]
  (let [{:keys [s3-presigner media-upload-bucket]} context
        {filename :filename
         file-id :fileId
         content-type :contentType
         organization-id :organizationId
         size :size} params
        key (format "organization_id=%s/%s.%s"
                    organization-id
                    (random-hex 20)
                    (fs/extension filename))
        s3-url (aws-s3.i/presign-put-url media-upload-bucket
                                         key
                                         content-type
                                         :presigner s3-presigner)]
    (-> (success-form :fields {:file-id file-id
                               :filename filename
                               :content-type content-type
                               :organization-id organization-id
                               :s3-bucket media-upload-bucket
                               :s3-url s3-url
                               :s3-method "PUT"
                               :s3-key key
                               :size size}
                      :router router)
        (html/render-partial))))
