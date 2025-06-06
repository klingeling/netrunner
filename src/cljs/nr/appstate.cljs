(ns nr.appstate
  (:require
   [cljs.core.async :refer [<!] :refer-macros [go]]
   [jinteki.i18n :as i18n]
   [jinteki.utils :refer [str->int]]
   [nr.ajax :refer [GET]]
   [clojure.string :as str]
   [reagent.core :as r]))

(defn- get-local-value
  "Read the value of the given key from localStorage. Return the default-value if no matching key found"
  [k default-value]
  (let [rtn (js->clj (.getItem js/localStorage k))]
    (if (nil? rtn) default-value rtn)))

(defn- load-visible-formats
  "Loading visible formats from localStorage. Accounting for the fact that js->clj doesn't handle sets"
  []
  (let [default-visible-formats #{"standard"
                                  "system-gateway"
                                  "core"
                                  "throwback"
                                  "startup"
                                  "eternal"
                                  "preconstructed"
                                  "casual"}
        serialized (get-local-value "visible-formats" "")]
    (if (empty? serialized) default-visible-formats (set (.parse js/JSON serialized)))))

(def valid-background-slugs
  #{"apex-bg" "custom-bg"
    "find-the-truth-bg" "freelancer-bg"
    "monochrome-bg" "mushin-no-shin-bg"
    "push-your-luck-bg" "rumor-mill-bg"
    "the-root-bg" "traffic-jam-bg"
    "worlds2020"})

(defn validate-options
  [opts]
  (-> opts
      (update :background #(or (valid-background-slugs %) "worlds2020"))
      (update :runner-board-order #(case %
                                     "true" "jnet"
                                     "false" "irl"
                                     %))))

;; we only support the following languages
;; if trs get added for new languages, I guess we need to update this
(def supported-languages
  #{"en" "fr" "ja" "ko" "pl" "pt" "ru" "zh-simp"})

(def nav-lang
  "en-us, en-uk, etc should just be en, fr-CA -> fr, en->en"
  (let [lang (some-> js/navigator.language (str/split #"-") first)]
    (cond
      ;; if we ever implement proper zh, fix this
      (= lang "zh") "zh-simp"
      (contains? supported-languages lang) lang
      :else "en")))

(def app-state
  (let [js-user (js->clj js/user :keywordize-keys true)]
    (r/atom {:active-page "/"
             :user js-user
             :options (-> {:background (get-local-value "background" "worlds2020")
                           :custom-bg-url (get-local-value "custom_bg_url" "https://nullsignal.games/wp-content/uploads/2022/07/Mechanics-of-Midnight-Sun-Header.png")
                           :corp-card-sleeve (get-local-value "corp-card-sleeve" "nsg-card-back")
                           :runner-card-sleeve (get-local-value "runner-card-sleeve" "nsg-card-back")
                           :card-zoom (get-local-value "card-zoom" "image")
                           :pin-zoom (= (get-local-value "pin-zoom" "false") "true")
                           :pronouns "none"
                           :language (get-local-value "language" nav-lang)
                           :default-format (get-local-value "default-format" "standard")
                           :show-alt-art true
                           :card-resolution "default"
                           :player-stats-icons (= (get-local-value "player-stats-icons" "true") "true")
                           :stacked-cards (= (get-local-value "stacked-cards" "true") "true")
                           :sides-overlap (= (get-local-value "sides-overlap" "true") "true")
                           :log-timestamps (= (get-local-value "log-timestamps" "true") "true")
                           :runner-board-order (get-local-value "runner-board-order" "irl")
                           :deckstats "always"
                           :gamestats "always"
                           :log-width (str->int (get-local-value "log-width" "300"))
                           :log-top (str->int (get-local-value "log-top" "419"))
                           :log-player-highlight (get-local-value "log-player-highlight" "blue-red")
                           :sounds (= (get-local-value "sounds" "true") "true")
                           :lobby-sounds (= (get-local-value "lobby_sounds" "true") "true")
                           :sounds-volume (str->int (get-local-value "sounds_volume" "100"))
                           :disable-websockets (= (get-local-value "disable-websockets" "false") "true")}
                          (merge (:options js-user))
                          (validate-options))
             :cards-loaded false
             :connected false
             :previous-cards {}
             :sets [] :mwl [] :cycles []
             :decks [] :decks-loaded false
             :stats (:stats js-user)
             :visible-formats (load-visible-formats)
             :channels {:general [] :america [] :europe [] :asia-pacific [] :united-kingdom [] :français []
                        :español [] :italia [] :polska [] :português [] :sverige [] :stimhack-league [] :русский []}
             :games [] :current-game nil})))

(go (let [lang (get-in @app-state [:options :language] "en")
          response (<! (GET (str "/data/language/" lang)))]
      (when (= 200 (:status response))
        (i18n/insert-lang! lang (:json response)))))

(defn current-gameid [app-state]
  (get-in @app-state [:current-game :gameid]))
