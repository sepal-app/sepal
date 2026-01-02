(ns sepal.app.routes.settings.users.index
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.authorization :as authz]
            [sepal.app.html :as html]
            [sepal.app.params :as params]
            [sepal.app.routes.settings.layout :as settings.layout]
            [sepal.app.routes.settings.users.routes :as users.routes]
            [sepal.app.ui.icons.lucide :as lucide]
            [sepal.app.ui.table :as table]
            [sepal.user.interface :as user.i]
            [zodiac.core :as z]))

(def Params
  [:map
   [:q {:optional true} :string]
   [:show-archived {:optional true} :boolean]])

(defn- role-badge [role]
  (let [colors {:admin "badge-primary"
                :editor "badge-accent"
                :reader "badge-ghost"}]
    [:span {:class (html/attr "badge badge-sm" (get colors role "badge-ghost"))}
     (name role)]))

(defn- status-badge [status]
  (let [colors {:active "badge-success"
                :archived "badge-error"
                :invited "badge-warning"}]
    [:span {:class (html/attr "badge badge-sm" (get colors status "badge-ghost"))}
     (name status)]))

(defn- csrf-hx-vals []
  (str "{\"__anti-forgery-token\": \"" (force *anti-forgery-token*) "\"}"))

(defn- role-select
  "Dropdown to change user role."
  [user]
  (let [current-role (:user/role user)]
    [:select {:id (str "role-select-" (:user/id user) "-" (name current-role))
              :class "select select-ghost select-sm leading-none"
              :autocomplete "off"
              :hx-post (z/url-for users.routes/update-role {:id (:user/id user)})
              :hx-swap "outerHTML"
              :hx-target "#users-table"
              :hx-vals (csrf-hx-vals)
              :name "role"}
     (for [role [:admin :editor :reader]]
       [:option {:value (name role)
                 :selected (= role current-role)}
        (name role)])]))

(defn- action-buttons
  "Action buttons for a user row."
  [user viewer]
  (when (and (authz/user-has-permission? viewer authz/users-edit)
             (not= (:user/id user) (:user/id viewer)))
    [:div {:class "flex gap-1"}
     ;; Resend invitation button for invited users
     (when (= :invited (:user/status user))
       [:button {:class "btn btn-ghost btn-xs"
                 :title "Resend invitation"
                 :hx-post (z/url-for users.routes/resend-invitation {:id (:user/id user)})
                 :hx-swap "none"
                 :hx-vals (csrf-hx-vals)
                 :hx-confirm "Resend invitation to this user?"}
        (lucide/mail :class "w-4 h-4")])
     ;; Archive/Activate buttons
     (if (= :archived (:user/status user))
       [:button {:class "btn btn-ghost btn-xs"
                 :title "Activate user"
                 :hx-post (z/url-for users.routes/activate {:id (:user/id user)})
                 :hx-swap "outerHTML"
                 :hx-target "#users-table"
                 :hx-vals (csrf-hx-vals)}
        (lucide/user-check :class "w-4 h-4")]
       [:button {:class "btn btn-ghost btn-xs"
                 :title "Archive user"
                 :hx-post (z/url-for users.routes/archive {:id (:user/id user)})
                 :hx-swap "outerHTML"
                 :hx-target "#users-table"
                 :hx-vals (csrf-hx-vals)
                 :hx-confirm "Are you sure you want to archive this user?"}
        (lucide/user-x :class "w-4 h-4")])]))

(defn- row-attrs [user]
  {:id (str "user-row-" (:user/id user))})

(defn- table-columns [viewer]
  [{:name "Name"
    :cell (fn [user] (or (:user/full-name user) "—"))}
   {:name "Email"
    :cell (fn [user] (:user/email user))}
   {:name "Role"
    :cell (fn [user]
            (if (and (authz/user-has-permission? viewer authz/users-change-role)
                     (not= (:user/id user) (:user/id viewer)))
              (role-select user)
              (role-badge (:user/role user))))}
   {:name "Status"
    :cell (fn [user] (status-badge (:user/status user)))}
   {:name ""
    :cell (fn [user] (action-buttons user viewer))}])

(defn- users-table [users viewer]
  (table/card-table
    (table/table :columns (table-columns viewer)
                 :rows users
                 :row-attrs row-attrs)))

(defn users-table-container
  "Renders the users table container. Exported for use by action handlers."
  [db viewer & {:keys [show-archived q]}]
  (let [users (user.i/get-all db
                              :q q
                              :exclude-status (when-not show-archived :archived))]
    [:div {:id "users-table"}
     (users-table users viewer)]))

(defn- filter-controls [params]
  [:div {:class "flex items-center justify-between mb-4"}
   [:div {:class "flex items-center"}
    [:input {:name "q"
             :type "search"
             :value (:q params)
             :placeholder "Search..."
             :class "input input-md bg-white w-96"
             :hx-get (z/url-for users.routes/index)
             :hx-trigger "keyup changed delay:300ms"
             :hx-select "#users-table"
             :hx-target "#users-table"
             :hx-swap "outerHTML"
             :hx-include "[name='show-archived']"}]
    [:label {:class "ml-8"}
     "Show archived"
     [:input {:type "checkbox"
              :name "show-archived"
              :value "true"
              :checked (:show-archived params)
              :class "ml-4"
              :hx-get (z/url-for users.routes/index)
              :hx-trigger "change"
              :hx-select "#users-table"
              :hx-target "#users-table"
              :hx-swap "outerHTML"
              :hx-include "[name='q']"}]]]
   [:a {:href (z/url-for users.routes/invite)
        :class "btn btn-primary"}
    (lucide/user-plus :class "w-4 h-4 mr-2")
    "Invite User"]])

(defn render [& {:keys [db viewer params]}]
  (settings.layout/layout
    :viewer viewer
    :current-route users.routes/index
    :category "Organization"
    :title "Users"
    :content-class "flex-1"
    :content
    [:div
     (filter-controls params)
     (users-table-container db viewer :show-archived (:show-archived params))]))

(defn handler [& {:keys [::z/context query-params viewer]}]
  (let [{:keys [db]} context
        params (params/decode Params query-params)]
    (render :db db
            :viewer viewer
            :params params)))
