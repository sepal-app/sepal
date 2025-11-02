(ns accession.src.sepal.accession.interface.spec.collection)

(def id :rowid)
(def collector :string)
(def collectors-code :string)
(def date :time/local-date)
(def locale :string)
(def altitude [:int {:max 29035} :min 0])
(def altitude-accuracy [:int {:max 29035} :min 0])
(def notes :string)
(def source-id :rowid)

(def Collection
  [:map
   [:collection/id id]
   [:collection/collector collector]
   [:collection/collectors-code collectors-code]
   [:collection/date date]
   [:collection/locale  locale]

   [:collection/altitude altitude]
   [:collection/altitude_accuracy altitude-accuracy]
   [:collection/notes notes]
   [:collection/source-id source-id]])
