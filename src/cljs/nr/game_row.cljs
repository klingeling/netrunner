(ns nr.game-row
  (:require
   [jinteki.utils :refer [superuser?]]
   [jinteki.preconstructed :refer [matchup-by-key]]
   [cljc.java-time.instant :as inst]
   [cljc.java-time.duration :as duration]
   [cljc.java-time.temporal.chrono-unit :as chrono]
   [nr.appstate :refer [app-state]]
   [nr.auth :refer [authenticated] :as auth]
   [nr.player-view :refer [player-view]]
   [nr.sounds :refer [resume-sound]]
   [nr.translations :refer [tr tr-format]]
   [nr.utils :refer [slug->format]]
   [nr.ws :as ws]
   [reagent.core :as r]))

(defn- reset-game-name
  [gameid]
  (authenticated
    (fn [_]
      (ws/ws-send! [:lobby/rename-game {:gameid gameid}]))))

(defn- delete-game
  [gameid]
  (authenticated
    (fn [_]
      (ws/ws-send! [:lobby/delete-game {:gameid gameid}]))))

(defn join-game
  ([lobby-state game action] (join-game lobby-state game action nil))
  ([lobby-state {:keys [gameid started]} action request-side]
   (authenticated
     (fn [_]
       (swap! lobby-state assoc :editing false)
       (ws/ws-send! [(case action
                       "join" :lobby/join
                       "watch" (if started :game/watch :lobby/watch)
                       "rejoin" :game/rejoin)
                     (cond-> {:gameid gameid}
                       request-side (conj {:request-side request-side}))])))))

(defn can-watch? [user game current-game editing]
  (or (superuser? @user)
      (and (:allow-spectator game)
           (not (or current-game editing)))))

(defn- watch-game-button
  [spectatorhands lobby-state game]
  (if-not spectatorhands
    [:button {:on-click #(do (join-game lobby-state game "watch")
                             (resume-sound))}
     (tr [:lobby_watch "Watch"])]
    (letfn [(join-fn [side]
              #(do (join-game lobby-state game "watch" side)
                   (resume-sound)))]
      [:div.split-button
       [:button {:on-click (join-fn nil)}
        (tr [:lobby_watch "Watch"])]
       [:button.dropdown-toggle {:data-toggle "dropdown"}
        [:b.caret]]
       [:ul.dropdown-menu.blue-shade
        [:a.block-link {:on-click (join-fn "Corp")}
       (tr [:lobby_corp-perspective "Corp Perspective"])]
      [:a.block-link {:on-click (join-fn "Runner")}
       (tr [:lobby_runner-perspective "Runner Perspective"])]
      [:a.block-link {:on-click (join-fn nil)}
       (tr [:lobby_both-perspective "Both"])]]])))

(defn- watch-protected-game-button
  [spectatorhands lobby-state game]
  (if-not spectatorhands
    [:button {:on-click #(if (:password game)
                           (authenticated
                             (fn [_]
                               (swap! lobby-state assoc :password-game {:game game :action "watch"})))
                           (do (join-game lobby-state game "watch")
                               (resume-sound)))}
     (tr [:lobby_watch "Watch"])]
    (letfn [(join-fn
              [side]
              #(if (:password game)
                 (authenticated
                   (fn [_]
                     (swap! lobby-state assoc :password-game {:game game :action "watch" :request-side side})))
                 (do (join-game lobby-state game "watch" side)
                     (resume-sound))))]
      [:div.split-button
       [:button {:on-click (join-fn nil)}
        (tr [:lobby_watch "Watch"])]
       [:button.dropdown-toggle {:data-toggle "dropdown"}
        [:b.caret]]
       [:ul.dropdown-menu.blue-shade
        [:a.block-link {:on-click (join-fn "Corp")}
       (tr [:lobby_corp-perspective "Corp Perspective"])]
      [:a.block-link {:on-click (join-fn "Runner")}
       (tr [:lobby_runner-perspective "Runner Perspective"])]
      [:a.block-link {:on-click (join-fn nil)}
       (tr [:lobby_both-perspective "Both"])]]])))

(defn watch-button [lobby-state user game current-game editing]
  (when (can-watch? user game current-game editing)
    (if (not (:password game))
      [watch-game-button (:spectatorhands game) lobby-state game]
      [watch-protected-game-button (:spectatorhands game) lobby-state game])))

(defn can-join? [user {:keys [room started players]} current-game editing]
  (if (= "tournament" room)
    (some #(= (:username user) (get-in % [:user :username])) players)
    (and (= 1 (count players))
         (not current-game)
         (not editing)
         (not started)
         (not (some #(= (:username user) (get-in % [:user :username])) players)))))

(defn join-button [lobby-state user game current-game editing]
  (when (can-join? user game current-game editing)
    (if (and (some #(= "Any Side" (:side %)) (:players game))
             (not (:password game)))
      [:div.split-button
       [:button {:on-click #(do (join-game lobby-state game "join")
                                (resume-sound))}
        (tr [:lobby_join "Join"])]
       [:button.dropdown-toggle {:data-toggle "dropdown"}
        [:b.caret]]
       [:ul.dropdown-menu.blue-shade
        [:a.block-link {:on-click #(do (join-game lobby-state game "join" "Corp")
                                       (resume-sound))}
         (tr [:lobby_as-corp "As Corp"])]
        [:a.block-link {:on-click #(do (join-game lobby-state game "join" "Runner")
                                       (resume-sound))}
         (tr [:lobby_as-runner "As Runner"])]]]
      [:button {:on-click #(if (:password game)
                             (authenticated
                               (fn [_]
                                 (swap! lobby-state assoc :password-game {:game game :action "join"})))
                             (do (join-game lobby-state game "join")
                                 (resume-sound)))}
       (tr [:lobby_join "Join"])])))

(defn can-rejoin? [user {:keys [started players original-players]} current-game editing]
  (and (= 1 (count players))
       (not current-game)
       (not editing)
       started
       (some #(= (:username @user) (get-in % [:user :username])) original-players)))

(defn rejoin-button [lobby-state user game current-game editing]
  (when (can-rejoin? user game current-game editing)
    [:button {:on-click #(if (:password game)
                           (authenticated
                             (fn [_]
                               (swap! lobby-state assoc :password-game {:game game :action "rejoin"})))
                           (do (join-game lobby-state game "rejoin")
                               (resume-sound)))}
     (tr [:lobby_rejoin "Rejoin"])]))

(defn mod-menu-popup [s user {gameid :gameid}]
  (when (and (:show-mod-menu @s)
             (superuser? @user))
    [:div.ctrl-menu
     [:div.panel.blue-shade.mod-menu
      [:div {:on-click #(do (reset-game-name gameid)
                            (swap! s assoc :show-mod-menu false))}
       (tr [:lobby_reset "Reset Game Name"])]
      [:div {:on-click #(do (delete-game gameid)
                            (swap! s assoc :show-mod-menu false))}
       (tr [:lobby_delete "Delete Game"])]
      [:div {:on-click #(swap! s assoc :show-mod-menu false)}
       (tr [:lobby_cancel "Cancel"])]]]))

(defn game-title [s user game]
  [:h4 {:on-click #(swap! s update :show-mod-menu not)
        :class (when (superuser? @user) "clickable")}
   (when (:save-replay game) "🟢")
   (when (:password game)
     [:<> "[" (tr [:lobby_private "PRIVATE"]) "] "])
   (:title game)
   (let [c (count (:spectators game))]
     (when (pos? c)
       [:<> " (" (tr [:lobby_spectator-count] {:cnt c}) ")"]))])

(defn- precon-span [precon]
  (when precon
    [:span.format-precon ": " (tr (:tr-tag (matchup-by-key precon)))]))

(defn- precon-under-span [precon]
  (when precon
    [:span.format-precon-deck-names (tr (:tr-underline (matchup-by-key precon)))]))

(defn- open-decklists-span [precon open-decklists]
  (when (and open-decklists (not precon))
    [:span.open-decklists " " (tr [:lobby_open-decklists-b "(open decklists)"])]))

(defn game-format [{fmt :format singleton? :singleton turmoil? :turmoil precon :precon open-decklists :open-decklists}]
  [:div {:class "game-format"}
   [:span.format-label (tr [:lobby_format "Format"]) ":  "]
   [:span.format-type (tr-format (slug->format fmt "Unknown"))]
   [precon-span precon]
   (when singleton? [:span.format-singleton " " (tr [:lobby_singleton-b "(singleton)"])])
   (when turmoil? [:span.turmoil " " (tr [:lobby_span-turmoil "(turmoil)"])])
   [open-decklists-span precon open-decklists]
   [precon-under-span precon]])

(defn- time-since
  "Helper method for game-time. Computes how many minutes since game start"
  [start]
  (let [now (inst/now)
        diff (duration/between start now)
        total-seconds (duration/get diff chrono/seconds)
        minutes (abs (quot total-seconds 60))]
    minutes))

(defn game-time [game]
  ;; NOTE: while running locally (repl), the :date field ends up being
  ;; native code, rather than Instant type. I don't understand this,
  ;; but when running via uberjar (or after reloading web/lobby.clj)
  ;; it is of the correct type. IDK how to fix the problem, but this
  ;; is a workable temporary fix - NBKelly, Jul 2024
  (when (and (:started game) (= (type (:date game)) (type (inst/now))))
    [:div.game-time [:span.game-time-emoji "⏰"] (str " " (time-since (:date game)) "m")]))

(defn players-row [{players :players :as game}]
  (into
    [:div]
    (map
      (fn [player]
        ^{:key (:_id player)}
        [player-view player game])
      players)))

(defn game-row [lobby-state game current-game editing]
  (r/with-let [state (r/atom {:show-mod-menu false})
               user (r/cursor app-state [:user])]
    [:div.gameline {:class (when (= (:gameid game) (:gameid current-game)) "active")}
     [watch-button lobby-state user game current-game editing]
     [join-button lobby-state user game current-game editing]
     [rejoin-button lobby-state user game current-game editing]
     [game-title state user game]
     [mod-menu-popup state user game]
     [game-format game]
     [game-time game]
     [players-row game]]))
