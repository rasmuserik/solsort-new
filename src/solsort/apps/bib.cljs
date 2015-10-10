(ns solsort.apps.bib
  (:require-macros
    [reagent.ratom :as ratom]
    [cljs.core.async.macros :refer  [go alt!]])

  (:require
    [cljs.test :refer-macros  [deftest testing is]]
    [goog.net.XhrIo]
    [goog.net.Jsonp]
    [solsort.util :refer [log <ajax host route]]
    [solsort.misc :refer [<seq<!]]
    [solsort.db :refer [db-url]]
    [clojure.string :as string]
    [reagent.core :as reagent :refer []]
    [cljs.core.async.impl.channels :refer [ManyToManyChannel]]
    [cljs.core.async :refer [>! <! chan put! take! timeout close!]]))

; NB: http://ogp.me/, http://schema.org, dublin-core, https://en.wikipedia.org/wiki/RDFa



; - search
;   - søgehistorik
;   - søgeresultater
;   - materialevisning
; - patron
;   - lånerstatus
;   - åbn e-bog etc
; - libraries
;   - find biblioteker
;   - dette bibliotek
; - ask
;   - Spørg en bibliotekar

(defn add-width-pos [boxes]
  (loop [box (first boxes)
         boxes (rest boxes)
         acc []
         w 0]
    (log 'atw box w)
    (if (nil? box)
      acc
      (recur (first boxes)
             (rest boxes) 
             (conj acc
                   (assoc box
                          :width-pos
                          w))
             (+ w (:ratio box))))))
(defn width-partition [boxes n]
  (let [total-width (+ (:width-pos (last boxes))
                       (:ratio (last boxes)))]
        (group-by #(js/Math.floor (/ (* n (:width-pos %)) total-width))
                  boxes)))

(defn width-height [boxes w]
  (let [boxes-width (reduce #(+ %1 (:ratio %2)) 0 boxes)
        boxes-height (/ w boxes-width)]
    (map 
      #(assoc %
              :height boxes-height
              :width (* (:ratio %) boxes-height))
      boxes)))

(def boxes 
  (->> (take 32 (range))
       (map
         (fn [i]
           (let [x (js/Math.random)
                 y (js/Math.random)
                 ]
             {:id i
              :coords [x y]
              :x x
              :y y
              :color (str "rgba(" (js/Math.floor (* 256 x)) "," (js/Math.floor (* 256 y)) ",128,1)")
              :ratio (+ 0.5 (js/Math.random))})
           ))
       (sort 
         (fn [a b]
           (- (:x a) (:x b))
           )) 
       (add-width-pos)
       ))


(js/console.log (clj->js (width-partition boxes 8)))

(defn box-layout []
  [:div
   {:style {:display "block"
            :position "relative"
            }}
   "blah"
   (into
     [:div]
      (map
        (fn [o]
          [:div "row"]
          )
        (map
        #(width-height % (- js/window.innerWidth 3))
        (width-partition boxes 8))
        )
      
      
     )
   (into 
     [:div
      {:style {:display "block"
               :position "relative"}}
      
      ]
     (map 
       (fn [o]
         [:div {:style {:position "absolute"
                        :display "inline-block"
                        :top (* 200 (:y o))
                        :left (* 500 (:x o))
                        :box-shadow "1px 1px 1px black"
                        :height 50
                        :width (* (:ratio o) 50)
                        :background-color (:color o)}}
          (str (:id o))])
       boxes))])

(route 
  "boxlayout"
  (fn [o]
    (go
      {:type :html
       :html [box-layout]
       }
      )))
;; #  

(defn bibobj [lid]
  (go (get (<! (<ajax (db-url (str "bib-old/" lid) ))) "info")))
(defn get-related [lid]
  (go (get (<! (<ajax (db-url (str "bib-old/" lid) ))) "related")))

(defn related-link [lid]
  (go
    #_(let [o (<! (bibobj lid))]
        (when o
          [:li
           [:a {:href (str "/bibdata/lid/" lid)
                }
            (first (or (o "title") [""]))
            (conj (into [:span " ("]
                        (interpose " & " (or (o "creator") [])))
                  ")")
            ]]))
    ; TODO 
    [:span " " [:a {:href (str "/bibdata/lid/" lid)} lid]]))


(def biblioteker
  [["bibliotek.dk" "http://bibliotek.dk/linkme.php?rec.id=870970-basis:"]
   ;["Horsens" "https://horsensbibliotek.dk/ting/object/870970-basis:"]
   ;["Vejle" "https://vejlebib.dk/ting/object/870970-basis:"]
   ])

(defn html-for-type [[k vs o]]
  (go
    (case k
      "title" [:h1 {:itemProp "name"} (first vs)]
      "creator" (into [:h2 "af "]
                      (interpose
                        " & "
                        (map (fn [v] [:span {:itemProp "creator"} v])
                             vs)))
      "date" [:div (first (o "type")) " udgivet " [:span {:itemProp "datePublished"} (first vs)]]
      "classification" [:div "DK5: " (string/join " & " vs)]
      "type" [:div "type: " (first vs)]
      "isbn" [:div "ISBN: " [:span {:itemProp "isbn"} (first vs)]]
      "lid" (into [:div.spaceabove "links: "
                   (if (o "isbn")
                     (let [isbn (first (o "isbn"))]
                       [:span
                        [:a {:href (str "http://www.worldcat.org/isbn/" isbn)
                             :itemProp "sameAs"} "WorldCat"] " "
                        [:a 
                         {:href (str "http://www.bogpriser.dk/Search/Result?isbn=" isbn)} 
                         "bogpriser.dk" ] " "
                        [:a {:href (str "https://books.google.dk/books?vid=ISBN" isbn)
                             :itemProp "sameAs"} "GoogleBøger"] " "])
                     " ")]
                  (interpose " "
                             (map (fn [[bib url]]
                                    [:a {:href (str url (first vs))
                                         :itemProp "sameAs"}
                                     bib])
                                  biblioteker)
                             ))
      "related" [:div.spaceabove 
                 "Anbefalinger: " 
                 (into [:ul]
                       (<! (<seq<! (map related-link (take 30 (rest vs))))))
                 "(anbefalingerne mangler midlertidigt titler, skyldes en større "
                 "omstrukturering in progress, - vil indeholde titler igen senere på måneden)" 

                 ]
      [:div k (str vs)])))

(defn itemtype [t]
  (str "http://schema.org/"
       (case (first t)
         "Bog" "Book"
         "Billedbog" "Book"
         "Dvd" "Movie"
         "Tidskriftasaf" "Article"
         (do
           (log 'bibdata 'warning-missing-itemtype t)
           "CreativeWork"))))
(defn entry [lid]
  (go
    (let [obj (or (<! (bibobj lid)) {})
          obj (conj obj ["lid" [lid]])
          obj (conj obj ["related" (map
                                     #(% "lid")
                                     (js->clj (<! (get-related lid))))])
          ks (filter obj ["title" "creator" "date" "classification"
                          ;"serieTitle"
                          "isbn" "lid" "related"])
          ]
      {:type :html
       :title (str (first (obj "title"))
                   " "
                   (seq (obj "creator"))
                   " - bibdata - solsort.com")
       :css {"body" {"margin" "5%"}
             ".spaceabove" {"margin-top" "1ex"}
             "ul" {"margin-top" "0"}}
       :html [:div.container
              (into []  (concat [:div {:itemScope "itemscope"
                                       :itemType (itemtype (obj "type"))}]
                                (filter identity
                                        (<! (<seq<!
                                              (map html-for-type
                                                   (map #(list % (obj %) obj) ks)))))
                                [[:hr]
                                 [:div [:small
                                        "Dette er et eksperiment med at lægge data om bøger online med semantisk opmarkering. Grunddata er en del af de nationalbibliografiske data som Kulturstyrelsen og Kulturministeriet stiller til fri brug. Anbefalingerne er baseret på lånstatistik som DBC frigjorde på hackathonen Hack4DK. Dette site, kildekode og anbefalingsalgoritme er lavet af solsort.com" ]]]
                                ))
              ;[:hr]
              ;[:div (string/join " " ks)]
              ;[:div (string/join " " (keys obj))]
              ;[:div (str (js->clj related))]
              ;[:div (str obj)]
              ]})))

(def sample-lids
  ["28511663" "28902239" "27999441" "27541062" "25862031"
   "20411724" "23917076" "29541167" "20476079" "29815860"
   "27594506" "25523911" "07203659" "44764873"])
(defn sample-lid [lid]
  (go
    [:li [:a {:href (str "/bibdata/lid/" lid)} lid]
     " " (first ((<! (bibobj lid)) "title"))]))

(defn default []
  (go
    {:type :html
     :title " bibdata - solsort.com"
     :css {"body" {"margin" "5%"}
           ".spaceabove" {"margin-top" "1ex"}
           "ul" {"margin-top" "0"}}
     :html [:div.container
            [:h1 "BibData"]
            "Eksempler:"
            (into [:ul] (<! (<seq<! (map sample-lid sample-lids))))
            [:small "Eksemplerne er udvalgt som 1., 10., 100., 1.000., 10.000., 20.000., 30.000., 40.000., 50.000., 60.000., 70.000., 80.000., 90.000., og 100.000. mest populære bog."]
            ]}))

(route 
  "bibdata"
  (fn [o]
    (let [path (string/split (o "path") "/")
          lid (nth path 2 "")
          kind (nth path 1 "")]
      (case kind
        "lid" (entry lid)
        (default)))))
;; #  
(route 
  "bib"
  (fn [o]
    (go 
      (let [path (string/split (o "path") "/")
            lid (nth path 2)
            kind (nth path 1)
            json  (<! (<ajax (db-url (str "bib-old/" lid) ) :result :json))]
        {:type :json
         :json
         (case kind
           "info" (aget json "stat")
           "related" (aget json "related")
           "wrong-path")}))))
