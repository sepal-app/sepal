(ns sepal.accession.interface.spec.source)

(def source-id :rowid)
(def sources-code :string)
(def accession-id :rowid)
(def source-detail-id :rowid)
(def source-type [:enum {:decode/store keyword
                         :encode/store name
                         :decode/params #(when (seq %) keyword)}
                  :expedition
                  :gene_bank
                  :field_station
                  :staff
                  :university_department
                  :club
                  :municipal_department
                  :commerical
                  :individual
                  :other])

(def Source
  [:map
   [:source/id source-id]
   [:source/sources-code sources-code]
   [:source/accession-id accession-id]
   [:source/source-detail-id source-detail-id]])

(def SourceDetail
  [:map
   [:source-detail/id source-detail-id]
   [:source-detail/name :string]
   [:source-detail/description :string]
   [:source-detail/phone :string]
   [:source-detail/email :string]
   [:source-detail/address :string]
   [:source-detail/source-type source-type]])
