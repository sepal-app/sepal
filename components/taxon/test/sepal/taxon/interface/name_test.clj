(ns sepal.taxon.interface.name-test
  "Botanical typography is correctness, not decoration. A botanist judges
   whether the software knows the domain by whether the names are set right.

   Counts below are from the WFO reference taxonomy loaded in development:
   453,167 taxa, of which 6,011 carry a hybrid marker, 26,214 ` subsp. `,
   25,919 ` var. ` and 756 ` f. `."
  (:require [clojure.test :refer [are deftest is testing]]
            [sepal.taxon.interface.name :as taxon.name]))

(deftest test-plain-binomial
  (is (= [{:text "Quercus alba" :role :scientific}]
         (taxon.name/segments "Quercus alba"))))

(deftest test-hybrid-marker-stays-upright
  (testing "6,011 reference names carry a × and it is never italic"
    (is (= [{:text "Nepenthes" :role :scientific}
            {:text " × " :role :upright}
            {:text "hookeriana" :role :scientific}]
           (taxon.name/segments "Nepenthes × hookeriana")))))

(deftest test-connecting-terms-stay-upright
  (testing "subsp. — 26,214 rows"
    (is (= [{:text "Eleocharis mamillata" :role :scientific}
            {:text " subsp. " :role :upright}
            {:text "austriaca" :role :scientific}]
           (taxon.name/segments "Eleocharis mamillata subsp. austriaca"))))
  (testing "var. — 25,919 rows"
    (is (= [{:text "Cyperus pangorei" :role :scientific}
            {:text " var. " :role :upright}
            {:text "ambiguus" :role :scientific}]
           (taxon.name/segments "Cyperus pangorei var. ambiguus"))))
  (testing "f. — 756 rows"
    (is (= [{:text "Carex kitaibeliana" :role :scientific}
            {:text " f. " :role :upright}
            {:text "balcanica" :role :scientific}]
           (taxon.name/segments "Carex kitaibeliana f. balcanica")))))

(deftest test-autonym-repeats-the-epithet
  (testing "Carex fusiformis subsp. fusiformis is a real and common shape"
    (is (= [{:text "Carex fusiformis" :role :scientific}
            {:text " subsp. " :role :upright}
            {:text "fusiformis" :role :scientific}]
           (taxon.name/segments "Carex fusiformis subsp. fusiformis")))))

(deftest test-cultivar-epithet-stays-upright
  (testing "ICNCP sets the epithet upright inside single quotes"
    (is (= [{:text "Acer palmatum " :role :scientific}
            {:text "'Sango-kaku'" :role :upright}]
           (taxon.name/segments "Acer palmatum 'Sango-kaku'")))))

(deftest test-cultivar-epithet-containing-an-apostrophe
  (testing "'Nuccio's Pearl' is a real cultivar — greedy quote pairing splits it
            after Nuccio and italicises the rest of the epithet"
    (is (= [{:text "Camellia japonica " :role :scientific}
            {:text "'Nuccio's Pearl'" :role :upright}]
           (taxon.name/segments "Camellia japonica 'Nuccio's Pearl'")))))

(deftest test-genus-plus-cultivar-with-no-species
  (is (= [{:text "Hosta " :role :scientific}
          {:text "'Sum and Substance'" :role :upright}]
         (taxon.name/segments "Hosta 'Sum and Substance'"))))

(deftest test-hybrid-and-cultivar-together
  (is (= [{:text "Nepenthes" :role :scientific}
          {:text " × " :role :upright}
          {:text "hookeriana " :role :scientific}
          {:text "'Chelsea'" :role :upright}]
         (taxon.name/segments "Nepenthes × hookeriana 'Chelsea'"))))

(deftest test-blank-and-nil
  (is (= [] (taxon.name/segments nil)))
  (is (= [] (taxon.name/segments "")))
  (is (= [] (taxon.name/segments "   "))))

(deftest test-a-lone-apostrophe-is-not-a-cultivar
  (testing "an unpaired quote must not swallow the name"
    (is (= [{:text "Quercus alba'" :role :scientific}]
           (taxon.name/segments "Quercus alba'")))))

(deftest test-parent-name
  (testing "a binomial hangs off its genus"
    (is (= "Acer" (taxon.name/parent-name "Acer palmatum")))
    (is (= "Acer" (taxon.name/parent-name "Acer × freemanii"))
        "the hybrid marker is not a word"))

  (testing "a cultivar hangs off the whole name before its epithet"
    (is (= "Acer palmatum" (taxon.name/parent-name "Acer palmatum 'Bloodgood'")))
    (is (= "Rosa" (taxon.name/parent-name "Rosa 'Peace'"))
        "a cultivar of a genus, not of a species"))

  (testing "a Group's own epithet is the last word, so it goes too"
    (is (= "Rhododendron" (taxon.name/parent-name "Rhododendron Ponticum Group")))
    (is (= "Brassica oleracea"
           (taxon.name/parent-name "Brassica oleracea Capitata Group"))))

  (testing "an infraspecific name hangs off what precedes the term"
    (is (= "Acer palmatum" (taxon.name/parent-name "Acer palmatum var. dissectum")))
    (is (= "Pinus nigra" (taxon.name/parent-name "Pinus nigra subsp. salzmannii")))
    (testing "and off the nearest one, not the first"
      (is (= "Acer palmatum subsp. amoenum"
             (taxon.name/parent-name "Acer palmatum subsp. amoenum var. matsumurae")))))

  (testing "a one-word name says nothing about what is above it"
    (is (nil? (taxon.name/parent-name "Acer")))
    (is (nil? (taxon.name/parent-name "Rosaceae")))
    (is (nil? (taxon.name/parent-name nil)))
    (is (nil? (taxon.name/parent-name "")))))

(deftest test-parent-name-and-guess-rank-agree
  (testing "the rank the parent name implies is what the auto-select checks a
            candidate against, so the two have to line up"
    (are [child parent-rank] (= parent-rank
                                (some-> (taxon.name/parent-name child)
                                        (taxon.name/guess-rank)))
      "Acer palmatum" :genus
      "Acer palmatum 'Bloodgood'" :species
      "Rosa 'Peace'" :genus
      "Acer palmatum var. dissectum" :species
      "Rhododendron Ponticum Group" :genus)))

(deftest test-guess-rank
  (testing "a quoted epithet is a cultivar, whatever else the name carries"
    (is (= :cultivar (taxon.name/guess-rank "Rosa 'Peace'")))
    (is (= :cultivar (taxon.name/guess-rank "Acer palmatum 'Sango-kaku'")))
    (is (= :cultivar (taxon.name/guess-rank "Camellia 'Nuccio's Pearl'"))
        "an epithet may contain an apostrophe of its own"))

  (testing "a trailing Group is a cultivar group"
    (is (= :group (taxon.name/guess-rank "Rhododendron Ponticum Group")))
    (is (= :group (taxon.name/guess-rank "Hosta Sieboldiana group"))))

  (testing "the connecting term decides, wherever it sits"
    (is (= :subspecies (taxon.name/guess-rank "Pinus nigra subsp. salzmannii")))
    (is (= :variety (taxon.name/guess-rank "Acer palmatum var. dissectum")))
    (is (= :form (taxon.name/guess-rank "Acer palmatum f. latilobatum")))
    (is (= :subvariety (taxon.name/guess-rank "Rosa canina subvar. dumalis")))
    (is (= :convariety (taxon.name/guess-rank "Brassica oleracea convar. capitata"))))

  (testing "a one-word name takes its rank from a reserved ending"
    (is (= :family (taxon.name/guess-rank "Rosaceae")))
    (is (= :order (taxon.name/guess-rank "Rosales")))
    (is (= :subfamily (taxon.name/guess-rank "Rosoideae")))
    (is (= :tribe (taxon.name/guess-rank "Roseae")))
    (is (= :subtribe (taxon.name/guess-rank "Rosinae")))
    (testing "and -oideae wins over -eae, which it ends with"
      (is (= :subfamily (taxon.name/guess-rank "Faboideae")))))

  (testing "otherwise the word count: one is a genus, two a species"
    (is (= :genus (taxon.name/guess-rank "Acer")))
    (is (= :species (taxon.name/guess-rank "Acer palmatum"))))

  (testing "a hybrid marker is not a rank — a nothospecies is filed as a
            species, so the marker drops out of the count"
    (is (= :species (taxon.name/guess-rank "Acer × freemanii")))
    (is (= :genus (taxon.name/guess-rank "× Fatshedera"))))

  (testing "it stays quiet rather than guess"
    (is (nil? (taxon.name/guess-rank nil)))
    (is (nil? (taxon.name/guess-rank "")))
    (is (nil? (taxon.name/guess-rank "   ")))
    (is (nil? (taxon.name/guess-rank "Acer palmatum dissectum"))
        "three bare words name no rank this can be sure of"))

  (testing "surrounding whitespace does not change the answer"
    (is (= :species (taxon.name/guess-rank "  Acer palmatum  ")))))
