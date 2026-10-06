(ns sepal.app.tag-links
  "Linking a tag to a record and unlinking it, with the activity event. The
  Tags tabs and the bulk routes both write through here."
  (:require [sepal.error.interface :as error.i]
            [sepal.tag.interface :as tag.i]
            [sepal.tag.interface.activity :as tag.activity]))

(defn resolve-or-create!
  "The tag named `tag-name`, matched case-insensitively, or a new one. A race
  on the same new name fails the unique constraint and comes back as an error
  value."
  [db tag-name created-by]
  (or (tag.i/get-by-name db tag-name)
      (let [created (tag.i/create! db {:name tag-name})]
        (when-not (error.i/error? created)
          (tag.activity/create! db tag.activity/created created-by created))
        created)))

(defn link!
  "True when this linked `tag`, false when the link already existed. Only a
  real link writes an event."
  [db tag resource-type resource-id created-by]
  (let [linked? (tag.i/tag! db (:tag/id tag) resource-id resource-type)]
    (when (true? linked?)
      (tag.activity/create-link! db tag.activity/linked created-by tag resource-type resource-id))
    linked?))

(defn unlink!
  "True when this removed the link, false when there was none."
  [db tag resource-type resource-id removed-by]
  (let [unlinked? (tag.i/untag! db (:tag/id tag) resource-id resource-type)]
    (when unlinked?
      (tag.activity/create-link! db tag.activity/unlinked removed-by tag resource-type resource-id))
    unlinked?))
