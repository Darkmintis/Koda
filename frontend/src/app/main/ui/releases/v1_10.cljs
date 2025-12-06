;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.main.ui.releases.v1-10
  (:require
   [app.main.ui.releases.common :as c]
   [rumext.v2 :as mf]))

(defmethod c/render-release-notes "1.10"
  [{:keys [klass finish version]}]
  (mf/html
   [:div.modal-overlay
    [:div.animated {:class klass}
     [:div.modal-container.onboarding.feature
      [:div.modal-left
       [:img {:src "images/beta-on.jpg" :border "0" :alt "Koda is now BETA"}]]
      [:div.modal-right
       [:div.modal-title
        [:h2 "Koda is now BETA"]]
       [:span.release "Beta version " version]
       [:div.modal-content
        [:p "Koda’s officially beta!"]
        [:p "We carefully analyzed everything important to us before taking this step. And now we’re ready to move forward onto the beta version.  Have a play around if you haven’t yet."]
        [:a {:href "https://koda.app/why-beta" :target "_blank"} "Learn why we made this decision."]]
       [:div.modal-navigation
        [:button.btn-secondary {:on-click finish} "Explore Koda Beta 1.10"]]]
      [:img.deco {:src "images/deco-left.png" :border "0"}]
      [:img.deco.right {:src "images/deco-right.png" :border "0"}]]]]))
