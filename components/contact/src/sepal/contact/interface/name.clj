(ns sepal.contact.interface.name
  "How a contact identifies itself in a list.

  Two people can share a name, so a bare name is not enough to pick between
  them — and the contacts table already answers this by showing business and
  email beside the name."
  (:require [clojure.string :as str]))

(defn label
  "The name, and the first thing that tells this contact apart from another of
   the same name: the business, then the email, then the phone.

   A contact with none of those is its name alone, and one whose business is
   its name does not say it twice. The accession form used to render
   `business (name)`, which came out as \" (Kew Seed Bank)\" for every contact
   without a business — most of them."
  [contact]
  (let [contact-name (:contact/name contact)
        detail (->> [(:contact/business contact)
                     (:contact/email contact)
                     (:contact/phone contact)]
                    (remove str/blank?)
                    (remove #(= % contact-name))
                    (first))]
    (if detail
      (format "%s (%s)" contact-name detail)
      contact-name)))
