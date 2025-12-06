(ns sepal.collection.interface.datum
  "Coordinate Reference System (CRS) datum constants mapping to EPSG SRID codes.
   
   These are commonly used geodetic datums for geographic coordinate systems.
   The SRID (Spatial Reference System Identifier) values are from the EPSG registry.")

;; Horizontal/Geographic datums (ordered chronologically)

(def ngvd-29
  "Sea Level Datum 1929 (National Geodetic Vertical Datum of 1929)"
  5102)

(def osgb-36
  "Ordnance Survey Great Britain 1936"
  4277)

(def sk-42
  "Systema Koordinat 1942 goda (Pulkovo 1942)"
  4284)

(def ed-50
  "European Datum 1950"
  4230)

(def sad-69
  "South American Datum 1969"
  4618)

(def grs-80
  "Geodetic Reference System 1980 (used as basis for NAD83, WGS84, etc.)"
  4019)

(def nad-83
  "North American Datum 1983"
  4269)

(def wgs-84
  "World Geodetic System 1984 - the most common global datum"
  4326)

(def navd-88
  "North American Vertical Datum 1988"
  5103)

(def etrs-89
  "European Terrestrial Reference System 1989"
  4258)

(def gcj-02
  "Chinese obfuscated datum 2002 (Mars Coordinates)
   Note: No official EPSG code exists. This is an unofficial code used by some systems."
  4490)

;; Default datum for new coordinates
(def default-srid wgs-84)

;; Options for dropdown/select fields
;; Format: [srid label]
(def datum-options
  "List of datum options for use in dropdown selects.
   Each entry is [srid label] where srid is the EPSG code."
  [[wgs-84 "WGS 84 - World Geodetic System 1984"]
   [nad-83 "NAD 83 - North American Datum 1983"]
   [etrs-89 "ETRS89 - European Terrestrial Ref. Sys. 1989"]
   [osgb-36 "OSGB36 - Ordnance Survey Great Britain 1936"]
   [ed-50 "ED50 - European Datum 1950"]
   [sad-69 "SAD69 - South American Datum 1969"]
   [sk-42 "SK-42 - Systema Koordinat 1942"]
   [grs-80 "GRS 80 - Geodetic Reference System 1980"]
   [gcj-02 "GCJ-02 - Chinese Datum 2002"]])