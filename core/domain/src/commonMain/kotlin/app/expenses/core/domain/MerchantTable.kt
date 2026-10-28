package app.expenses.core.domain

/**
 * One well-known merchant: how its name is shown ([display]), the starter category it usually belongs to ([category], a category *name*, or null when the name is only worth cleaning up),
 * and every way the name shows up in a UPI address, a payee string or an SMS ([aliases], lower case words; the display name itself is always one of them). [display] null is a
 * category keyword that is not a merchant ("electricity"): it files a payment but never renames it.
 */
class Merchant(val display: String?, val category: String?, val aliases: List<String>)

/**
 * The data behind [DefaultRules]: Indian merchants and utilities by their public brand names, **written from public knowledge of the brands, not from anyone's transactions**. To add a
 * merchant add one `m(...)` line; `DefaultRulesTest` refuses an alias two merchants claim, a category outside the starter seven, and a name that is not a whole word. An alias is a
 * run of words; a name that starts with a merchant and goes on with words that are neither company noise nor shop words ("Ola Sharma", "Uber Singh") is a person and gets no category
 * (see [DefaultRules]); punctuation does not matter ("domino's" is the words `domino` and `s`), and what follows an `@` in a UPI address (the bank's handle) is never read, so "x@paytm" is not Paytm.
 */
internal object MerchantTable {
    const val FOOD = "Food"; const val GROCERIES = "Groceries"; const val TRANSPORT = "Transport"; const val BILLS = "Bills"; const val SHOPPING = "Shopping"; const val HEALTH = "Health"; const val FUN = "Fun"

    /** The categories a starter can name: the app's own seven starters. A name only takes effect when the person still has a visible category of that name. */
    val CATEGORIES = setOf(FOOD, GROCERIES, TRANSPORT, BILLS, SHOPPING, HEALTH, FUN)

    private fun m(display: String, category: String?, vararg aliases: String) = Merchant(display, category, (listOf(display.lowercase()) + aliases).distinct())
    private fun kw(category: String, vararg words: String) = Merchant(null, category, words.toList())

    val all: List<Merchant> = listOf(
        // ---- Food: delivery, restaurants, cafes, sweets
        m("Swiggy", FOOD, "swiggyupi", "swiggy food", "bundl technologies"), m("Zomato", FOOD, "zomato order", "zomato online"),
        m("Domino's", FOOD, "dominos", "dominos pizza", "domino's pizza", "jubilant foodworks"), m("KFC", FOOD, "kfc india", "devyani international"), m("McDonald's", FOOD, "mcdonalds", "hardcastle restaurants"),
        m("Starbucks", FOOD, "tata starbucks"), m("Pizza Hut", FOOD), m("Burger King", FOOD), m("Box8", FOOD), m("Faasos", FOOD), m("Chaayos", FOOD), m("Chai Point", FOOD),
        m("Behrouz Biryani", FOOD, "behrouz"), m("Haldiram's", FOOD, "haldirams", "haldiram"), m("Barbeque Nation", FOOD), m("EatSure", FOOD, "eat sure"), m("Subway", FOOD, "subway india"),
        m("Wow! Momo", FOOD, "wow momo", "wowmomo"), m("Cafe Coffee Day", FOOD, "ccd", "coffee day"), m("Costa Coffee", FOOD), m("Dunkin'", FOOD, "dunkin", "dunkin donuts"),
        m("Baskin Robbins", FOOD, "baskin robbins india"), m("Theobroma", FOOD), m("Mad Over Donuts", FOOD), m("Krispy Kreme", FOOD), m("Taco Bell", FOOD), m("Popeyes", FOOD),
        m("Burger Singh", FOOD), m("Oven Story Pizza", FOOD, "ovenstory", "oven story"), m("Biryani By Kilo", FOOD, "biryani by kilo"), m("Paradise Biryani", FOOD, "paradise food court"),
        m("Bikanervala", FOOD, "bikanervala foods"), m("Sagar Ratna", FOOD), m("Saravana Bhavan", FOOD, "saravana bhavan", "hotel saravana bhavan"), 
        // Decision (review D-m2): "a2b" is also a travel and courier name ("A2B Travels"), so only the full name of the sweets and restaurant chain is an alias.
        m("Adyar Ananda Bhavan", FOOD),
        // Decision: bare "mtr" is gone; "MTR Enterprises" or "MTR Motors" are not the food brand. The brand is MTR Foods, or Mavalli Tiffin Rooms the restaurant.
        m("MTR Foods", FOOD, "mavalli tiffin rooms", "mtr restaurant"), m("Third Wave Coffee", FOOD), m("Blue Tokai", FOOD, "blue tokai coffee"), m("Chai Sutta Bar", FOOD), m("Nando's", FOOD, "nandos"),
        m("Cinnabon", FOOD), m("Dineout", FOOD, "swiggy dineout"), m("EazyDiner", FOOD, "eazydiner", "eazy diner"), m("FreshMenu", FOOD, "fresh menu"), m("Eat.fit", FOOD, "eatfit", "eat fit"),
        m("Rebel Foods", FOOD), m("Zepto Cafe", FOOD), m("Wendy's", FOOD, "wendys"), m("Pizza Express", FOOD),
        m("Barista", FOOD, "barista coffee", "barista lavazza"), m("Chili's", FOOD, "chilis"), m("Biryani Blues", FOOD),
        m("Punjab Grill", FOOD), m("Mainland China", FOOD), m("Nirula's", FOOD, "nirulas"), m("Keventers", FOOD), m("Naturals Ice Cream", FOOD),
        // ---- Groceries: quick commerce, supermarkets, dairy, meat
        m("BigBasket", GROCERIES, "big basket", "bb daily", "bbdaily", "supermarket grocery supplies"), m("Blinkit", GROCERIES, "grofers", "blink commerce"), m("Zepto", GROCERIES, "kiranakart"), m("DMart", GROCERIES, "d mart", "avenue supermarts", "dmart ready"),
        m("JioMart", GROCERIES, "jio mart"), m("Swiggy Instamart", GROCERIES, "instamart"), m("Milkbasket", GROCERIES, "milk basket"), m("Licious", GROCERIES), m("Reliance Fresh", GROCERIES),
        m("Nature's Basket", GROCERIES, "natures basket"), m("Reliance Smart", GROCERIES, "reliance smart bazaar", "smart bazaar"), m("More Retail", GROCERIES, "more supermarket", "more megastore"),
        m("Spencer's", GROCERIES, "spencers"), m("Star Bazaar", GROCERIES, "star bazar"), m("Big Bazaar", GROCERIES, "big bazar"), m("Easyday", GROCERIES, "easy day"), m("SPAR Hypermarket", GROCERIES),
        m("Metro Cash & Carry", GROCERIES, "metro cash carry", "metro cash and carry"), m("Country Delight", GROCERIES, "countrydelight"), m("Akshayakalpa", GROCERIES, "akshaya kalpa"),
        m("FreshToHome", GROCERIES, "fresh to home"), m("Amazon Fresh", GROCERIES, "amazon fresh"), m("Supr Daily", GROCERIES, "suprdaily"), m("Heritage Fresh", GROCERIES), m("Amul", GROCERIES, "amul parlour", "amul dairy"),
        m("Mother Dairy", GROCERIES), m("Lulu Hypermarket", GROCERIES, "lulu hyper"), m("Ratnadeep", GROCERIES, "ratnadeep supermarket"), m("Nandini Milk", GROCERIES, "nandini dairy"),
        m("Hatsun Agro", GROCERIES, "arun ice cream"), m("Aavin", GROCERIES), m("Vijaya Dairy", GROCERIES),
       
        // ---- Transport: cabs, trains, buses, flights, fuel, metro, tolls
        // Decision: "Uber Eats" is food delivery (its own entry, two words beat "uber"); a name that merely starts with a merchant, like "Uber Singh", is handled by DefaultRules (person-like payees).
        m("Uber Eats", FOOD),
        m("Uber", TRANSPORT, "uber india", "uber india systems", "uber trip", "uber ride"), m("Ola", TRANSPORT, "ola cabs", "olacabs", "ani technologies"), m("Rapido", TRANSPORT, "roppen transportation"),
        m("redBus", TRANSPORT, "red bus"), m("IRCTC", TRANSPORT, "indian railway catering"), m("FASTag", TRANSPORT, "fast tag", "netc fastag"), m("Namma Yatri", TRANSPORT, "nammayatri"),
        m("IndiGo", TRANSPORT, "interglobe aviation"), m("Air India", TRANSPORT, "air india express", "airindia"), m("Vistara", TRANSPORT, "tata sia airlines"), m("Indian Oil", TRANSPORT, "iocl", "indianoil", "indian oil corporation"),
        m("BPCL", TRANSPORT, "bharat petroleum", "bharat petroleum corporation"), m("HPCL", TRANSPORT, "hindustan petroleum", "hindustan petroleum corporation", "hp petrol", "hp pay"), m("Jio-bp", TRANSPORT, "jio bp", "jio bp pulse"),
        m("Nayara Energy", TRANSPORT, "nayara"), m("Reliance Petrol", TRANSPORT, "reliance petro"), m("SpiceJet", TRANSPORT, "spice jet"), m("Akasa Air", TRANSPORT, "akasa"), m("AirAsia", TRANSPORT, "air asia"),
        m("MakeMyTrip", TRANSPORT, "make my trip"), m("Goibibo", TRANSPORT), m("Cleartrip", TRANSPORT, "clear trip"), m("EaseMyTrip", TRANSPORT, "ease my trip"), m("ixigo", TRANSPORT),
        m("RailYatri", TRANSPORT, "rail yatri"), m("ConfirmTkt", TRANSPORT, "confirm tkt"), m("AbhiBus", TRANSPORT, "abhi bus"), m("Zingbus", TRANSPORT, "zing bus"), m("FlixBus", TRANSPORT, "flix bus"),
        m("Delhi Metro", TRANSPORT, "dmrc", "delhi metro rail"), m("Namma Metro", TRANSPORT, "bmrcl", "bangalore metro"), m("Chennai Metro", TRANSPORT, "cmrl", "chennai metro rail"),
        m("Mumbai Metro", TRANSPORT, "mmrda", "mumbai metro one"), m("Hyderabad Metro", TRANSPORT, "l&t metro", "hmrl"), m("BMTC", TRANSPORT, "bangalore metropolitan transport"),
        m("KSRTC", TRANSPORT), m("TSRTC", TRANSPORT), m("MSRTC", TRANSPORT), m("APSRTC", TRANSPORT), m("Park Plus", TRANSPORT, "parkplus"), m("Yulu", TRANSPORT), m("Zoomcar", TRANSPORT, "zoom car"),
        m("Drivezy", TRANSPORT), m("BluSmart", TRANSPORT, "blu smart"), m("inDrive", TRANSPORT, "indrive", "indriver"), m("Meru Cabs", TRANSPORT, "meru"), m("Quick Ride", TRANSPORT, "quickride"),
        m("Paytm FASTag", TRANSPORT, "paytm fastag"),
        m("Shell India", TRANSPORT, "shell petrol"), m("Essar Oil", TRANSPORT), m("Castrol", TRANSPORT, "castrol india"), m("Vogo", TRANSPORT, "vogo rentals"),
        // ---- Bills: telecom, power, gas, water, broadband, TV, insurers
        m("Jio", BILLS, "reliance jio", "jio recharge", "jio prepaid", "jio postpaid", "jiopay", "reliance jio infocomm"), m("Airtel", BILLS, "bharti airtel", "airtel prepaid", "airtel postpaid"),
        m("Vodafone Idea", BILLS, "vodafone", "vi postpaid", "vi prepaid", "idea cellular"), m("BSNL", BILLS, "bharat sanchar nigam"), m("MTNL", BILLS), m("BESCOM", BILLS, "bangalore electricity"),
        m("TNEB", BILLS, "tangedco", "tamil nadu electricity"), m("MSEDCL", BILLS, "mahavitaran", "mahadiscom"), m("Tata Power", BILLS, "tata power delhi", "tata power mumbai"), m("Adani Electricity", BILLS, "adani electricity mumbai"),
        m("Bharat Gas", BILLS, "bharatgas", "bharat petroleum gas"), m("Indane", BILLS, "indane gas", "indian oil gas"), m("HP Gas", BILLS, "hpgas", "hindustan petroleum gas"),
        m("Mahanagar Gas", BILLS, "mgl"), m("Indraprastha Gas", BILLS, "igl"), m("Adani Total Gas", BILLS, "adani gas"), m("Gujarat Gas", BILLS), m("ACT Fibernet", BILLS, "act broadband", "atria convergence"),
        m("Hathway", BILLS, "hathway broadband"), m("Tata Play", BILLS, "tata play fiber", "tata play binge", "tata sky", "tatasky"), m("Dish TV", BILLS, "dishtv"), m("d2h", BILLS, "videocon d2h", "dish d2h"),
        m("Sun Direct", BILLS, "sundirect"), m("LIC", BILLS, "life insurance corporation", "lic of india", "lic premium"), m("BSES", BILLS, "bses rajdhani", "bses yamuna"), m("CESC", BILLS),
        m("Torrent Power", BILLS), m("JioFiber", BILLS, "jio fiber", "jio airfiber", "jio air fiber"), m("Airtel Xstream", BILLS, "airtel xstream fiber", "airtel digital tv"), m("Excitel", BILLS, "excitel broadband"),
        m("Tikona", BILLS), m("YOU Broadband", BILLS, "you broadband"), m("Delhi Jal Board", BILLS, "djb"), m("BWSSB", BILLS, "bangalore water supply"), m("HMWSSB", BILLS, "hyderabad metro water"),
        m("KSEB", BILLS, "kerala state electricity"), m("APSPDCL", BILLS), m("TSSPDCL", BILLS), m("UPPCL", BILLS), m("PSPCL", BILLS), m("WBSEDCL", BILLS), m("DHBVN", BILLS), m("UHBVN", BILLS), m("JVVNL", BILLS),
        m("PGVCL", BILLS), m("UGVCL", BILLS), m("DGVCL", BILLS), m("MGVCL", BILLS), m("CSPDCL", BILLS), m("TPDDL", BILLS), m("HESCOM", BILLS), m("MESCOM", BILLS), m("GESCOM", BILLS), m("CHESCOM", BILLS),
        m("HDFC Life", BILLS, "hdfc life insurance"), m("ICICI Prudential", BILLS, "icici pru", "icici prudential life"), m("SBI Life", BILLS, "sbi life insurance"), m("Max Life", BILLS, "max life insurance"),
        m("Bajaj Allianz", BILLS, "bajaj allianz life"), m("Tata AIA", BILLS, "tata aia life"), m("Digit Insurance", BILLS, "go digit"), m("Acko", BILLS, "acko general insurance"), m("Policybazaar", BILLS, "policy bazaar"),
        m("New India Assurance", BILLS), m("Kotak Life", BILLS, "kotak life insurance"), m("PNB MetLife", BILLS, "pnb metlife"), m("Aditya Birla Sun Life", BILLS, "absli"), m("Reliance General Insurance", BILLS),
        m("Spectranet", BILLS, "spectra net"), m("Den Networks", BILLS, "den broadband"), m("Siti Cable", BILLS, "siti networks"),
        kw(BILLS, "electricity", "broadband", "recharge"),
        // ---- Shopping: marketplaces, fashion, electronics, home, beauty
        m("Amazon", SHOPPING, "amazon india", "amazon seller services", "amazon in", "amzn"), m("Amazon Pay", SHOPPING, "amazonpay", "amazon pay india", "amazon payments"), m("Flipkart", SHOPPING, "flipkart internet", "flipkart payments"),
        m("Myntra", SHOPPING), m("Ajio", SHOPPING), m("Meesho", SHOPPING), m("Nykaa", SHOPPING, "nykaa fashion", "fsn e commerce"), m("Tata CLiQ", SHOPPING, "tata cliq", "tatacliq"),
        m("Decathlon", SHOPPING, "decathlon sports"), m("IKEA", SHOPPING, "ikea india"), m("Croma", SHOPPING, "infiniti retail", "chroma"), m("Lenskart", SHOPPING, "lenskart solutions"), m("Reliance Digital", SHOPPING),
        m("Shoppers Stop", SHOPPING), m("Pantaloons", SHOPPING), m("Westside", SHOPPING), m("Max Fashion", SHOPPING), m("Uniqlo", SHOPPING), m("Reliance Trends", SHOPPING),
        m("FirstCry", SHOPPING, "first cry"), m("Mamaearth", SHOPPING, "honasa consumer"), m("Purplle", SHOPPING), m("Tira Beauty", SHOPPING, "tira"), m("Sephora", SHOPPING), m("Bewakoof", SHOPPING),
        m("Snapdeal", SHOPPING), m("ShopClues", SHOPPING), m("Tata Neu", SHOPPING, "tataneu"), m("Reliance Retail", SHOPPING), m("Vijay Sales", SHOPPING), m("Poorvika", SHOPPING),
        m("Sangeetha Mobiles", SHOPPING, "sangeetha"), m("Apple Store", SHOPPING, "apple india", "apple retail"), m("OnePlus", SHOPPING, "one plus"), m("boAt", SHOPPING, "boat lifestyle", "imagine marketing"),
        m("Tanishq", SHOPPING, "titan company"), m("CaratLane", SHOPPING, "caratlane", "carat lane"), m("Malabar Gold", SHOPPING, "malabar gold diamonds"), m("Kalyan Jewellers", SHOPPING),
        m("Bata", SHOPPING, "bata india"), m("Puma", SHOPPING, "puma sports india") /* "Puma Transport" is not the brand: DefaultRules gives a merchant name followed by a word that is neither company noise nor a shop word no category */, m("Nike", SHOPPING, "nike india"), m("Adidas", SHOPPING, "adidas india"), m("Allen Solly", SHOPPING), m("Van Heusen", SHOPPING),
        m("Peter England", SHOPPING), m("Fabindia", SHOPPING), m("Pepperfry", SHOPPING), m("Urban Ladder", SHOPPING, "urbanladder"), m("Wakefit", SHOPPING), m("Home Centre", SHOPPING, "homecentre"),
        m("Hamleys", SHOPPING), m("Crossword Bookstores", SHOPPING), m("Vishal Mega Mart", SHOPPING, "vishal megamart"), m("Zivame", SHOPPING), m("Lifestyle Stores", SHOPPING, "lifestyle international"),
        m("Mi Store", SHOPPING, "xiaomi"), m("Samsung Shop", SHOPPING, "samsung india", "samsung store"),
        m("Jockey", SHOPPING, "page industries"), m("Levi's", SHOPPING, "levis", "levi strauss"), m("Marks & Spencer", SHOPPING, "marks and spencer", "marks spencer"),
        m("Titan Eye Plus", SHOPPING, "titan eyeplus"), m("Pothys", SHOPPING), m("Nalli Silks", SHOPPING, "nalli"), m("Louis Philippe", SHOPPING), m("Raymond", SHOPPING, "the raymond shop"),
        m("Libas", SHOPPING), m("Clovia", SHOPPING), m("Bombay Shaving Company", SHOPPING, "bombay shaving"), m("Sugar Cosmetics", SHOPPING), m("Wow Skin Science", SHOPPING),
       
        // ---- Health: pharmacies, labs, hospitals, fitness, health insurers
        /* Decision: bare "apollo" stays (a legacy starter, and most payees named so are the pharmacy or hospital); "Apollo Tyres Ltd" gets nothing because "tyres" is not a shop word (DefaultRules). */ m("Apollo", HEALTH, "apollo pharmacy", "apollo clinic", "apollo hospitals", "apollo 24 7", "apollo diagnostics"), m("PharmEasy", HEALTH, "pharm easy", "api holdings"), m("Tata 1mg", HEALTH, "1mg"),
        m("Netmeds", HEALTH, "net meds"), m("Practo", HEALTH, "practo technologies"), m("MedPlus", HEALTH, "med plus", "medplus mart"), m("Cult.fit", HEALTH, "cult fit", "cultfit", "cure fit", "curefit"),
        m("Dr Lal PathLabs", HEALTH, "lal pathlabs", "lal path labs"), m("Thyrocare", HEALTH), m("Metropolis Healthcare", HEALTH), m("SRL Diagnostics", HEALTH, "srl"), m("Redcliffe Labs", HEALTH, "redcliffe"),
        m("Healthians", HEALTH), m("Orange Health", HEALTH), m("Max Healthcare", HEALTH, "max hospital", "max super speciality"), m("Fortis", HEALTH, "fortis healthcare", "fortis hospital"),
        m("Manipal Hospitals", HEALTH, "manipal hospital"), m("Narayana Health", HEALTH, "narayana hrudayalaya"), m("Medanta", HEALTH, "global health pvt"), m("AIIMS", HEALTH),
        m("Cloudnine", HEALTH, "cloudnine hospital"), m("Rainbow Hospitals", HEALTH, "rainbow children"), m("Care Hospitals", HEALTH, "care hospital"), m("Wellness Forever", HEALTH),
        m("HealthifyMe", HEALTH, "healthify me"), m("Truemeds", HEALTH, "true meds"), m("Dr Morepen", HEALTH, "morepen"), m("Zeno Health", HEALTH), m("Jan Aushadhi", HEALTH, "jan aushadhi kendra"),
        m("MFine", HEALTH), m("DocsApp", HEALTH, "docs app"), m("Star Health", HEALTH, "star health insurance", "star health allied"), m("Niva Bupa", HEALTH, "max bupa"), m("Care Health Insurance", HEALTH, "care health"),
        m("Aster Medcity", HEALTH, "aster hospital"), m("Sankara Nethralaya", HEALTH), m("Dr Agarwal's", HEALTH, "dr agarwals", "agarwal eye hospital"), m("Vasan Eye Care", HEALTH),
        m("Pristyn Care", HEALTH, "pristyncare"), m("Even Healthcare", HEALTH), m("Ayu Health", HEALTH, "ayuhealth"), m("Kauvery Hospital", HEALTH, "kauvery"), m("Sir Ganga Ram Hospital", HEALTH, "ganga ram hospital"),
        m("Clove Dental", HEALTH),
        // ---- Fun: streaming, music, movies, games, subscriptions, parks
        m("Netflix", FUN, "netflix india", "netflix com"), m("Prime Video", FUN, "amazon prime video"), m("Amazon Prime", FUN, "amazon prime membership"), m("Disney+ Hotstar", FUN, "hotstar", "disney hotstar", "disney plus hotstar", "star india"),
        m("JioHotstar", FUN, "jio hotstar", "jiohotstar"), m("JioCinema", FUN, "jio cinema", "jiocinema"), m("Spotify", FUN, "spotify india"), m("BookMyShow", FUN, "book my show", "bigtree entertainment"), m("PVR", FUN, "pvr inox", "pvr cinemas"),
        m("INOX", FUN, "inox leisure", "inox movies"), m("YouTube Premium", FUN, "youtube premium", "youtube music"), m("SonyLIV", FUN, "sony liv", "sonyliv"), m("ZEE5", FUN, "zee 5"), kw(FUN, "disney"),
        m("Apple Music", FUN), m("Gaana", FUN), m("JioSaavn", FUN, "jio saavn", "saavn"), m("Wynk Music", FUN, "wynk"), m("Audible", FUN), m("Kindle Unlimited", FUN), m("Xbox", FUN, "xbox game pass"),
        m("PlayStation", FUN, "playstation store", "sony playstation"), m("Dream11", FUN, "dream 11", "sporta technologies"), m("Zupee", FUN), m("Cinepolis", FUN, "cinepolis india"),
        m("Carnival Cinemas", FUN), m("Miraj Cinemas", FUN), m("Paytm Insider", FUN), m("hoichoi", FUN), m("Sun NXT", FUN, "sunnxt"), m("Voot", FUN), m("MX Player", FUN, "mxplayer"),
        m("Eros Now", FUN, "erosnow"), m("ALTBalaji", FUN, "alt balaji"), m("Apple TV", FUN, "apple tv plus"), m("Crunchyroll", FUN), m("Hungama", FUN, "hungama play"),
        m("iCloud", FUN, "icloud storage"), m("Google Play", FUN, "google play store", "google play music", "google play games"), m("Wonderla", FUN, "wonderla holidays"), m("Imagica", FUN, "adlabs imagica"),
        m("Ramoji Film City", FUN), m("Nintendo", FUN, "nintendo eshop"),
       
        // ---- Display only: payment apps, a clean name and no category
        m("Paytm", null, "paytm wallet", "one97 communications"), m("PhonePe", null, "phone pe", "phonepe wallet"), m("Google Pay", null, "gpay", "google pay india", "google india digital"),
    )
}
