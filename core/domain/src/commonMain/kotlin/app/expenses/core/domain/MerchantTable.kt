package app.expenses.core.domain

/**
 * One well-known merchant: how its name is shown ([display]), the starter category it usually belongs to ([category], a category *name*, or null when the name is only worth cleaning up),
 * and every way the name shows up in a UPI address, a payee string or an SMS ([aliases], lower case words; the display name itself is always one of them). [display] null is a
 * category keyword that is not a merchant ("electricity"): it files a payment but never renames it.
 */
class Merchant(val display: String?, val category: String?, val aliases: List<String>, /** A better home among the newer starter categories ([StarterCategories.EXPENSE_MORE]), used when the person has that category; [category] is then the fallback. */ val home: String? = null)

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

    // The newer starter categories ([StarterCategories.EXPENSE_MORE]). A merchant may name one as its [Merchant.home]: a person who has that category (every new install, and anyone who added it from Suggested
    // categories) gets it suggested; a person who has not keeps the coarse [Merchant.category] the merchant always had, so nothing changes for an existing ledger that did not ask for it.
    private const val RENT = "Rent"; private const val UTILITIES = "Utilities"; private const val MOBILE = "Internet and mobile"; private const val INSURANCE = "Insurance"; private const val EDUCATION = "Education"
    private const val FUEL = "Fuel"; private const val TRAVEL = "Travel"; private const val SUBS = "Subscriptions"; private const val INVEST = "Investments"; private const val LOANS = "Loan EMI"
    private const val CARE = "Personal care"; private const val GIVING = "Gifts and donations"; private const val TAXES = "Taxes"

    /** The better home of an EXISTING merchant (by display name). Its [Merchant.category] is unchanged. */
    val HOMES: Map<String, String> = buildMap {
        for (n in listOf("Netflix", "Prime Video", "Amazon Prime", "Disney+ Hotstar", "JioHotstar", "JioCinema", "Spotify", "YouTube Premium", "SonyLIV", "ZEE5", "Apple Music", "Gaana", "JioSaavn", "Wynk Music", "Audible",
            "Kindle Unlimited", "Xbox", "hoichoi", "Sun NXT", "Voot", "MX Player", "Eros Now", "ALTBalaji", "Apple TV", "Crunchyroll", "Hungama", "iCloud")) put(n, SUBS)
        for (n in listOf("Indian Oil", "BPCL", "HPCL", "Jio-bp", "Nayara Energy", "Reliance Petrol", "Shell India", "Essar Oil")) put(n, FUEL)
        for (n in listOf("IRCTC", "redBus", "IndiGo", "Air India", "Vistara", "SpiceJet", "Akasa Air", "AirAsia", "MakeMyTrip", "Goibibo", "Cleartrip", "EaseMyTrip", "ixigo", "RailYatri", "ConfirmTkt", "AbhiBus", "Zingbus", "FlixBus")) put(n, TRAVEL)
        for (n in listOf("BESCOM", "TNEB", "MSEDCL", "Tata Power", "Adani Electricity", "Bharat Gas", "Indane", "HP Gas", "Mahanagar Gas", "Indraprastha Gas", "Adani Total Gas", "Gujarat Gas", "BSES", "CESC", "Torrent Power",
            "Delhi Jal Board", "BWSSB", "HMWSSB", "KSEB", "APSPDCL", "TSSPDCL", "UPPCL", "PSPCL", "WBSEDCL", "DHBVN", "UHBVN", "JVVNL", "PGVCL", "UGVCL", "DGVCL", "MGVCL", "CSPDCL", "TPDDL", "HESCOM", "MESCOM", "GESCOM", "CHESCOM")) put(n, UTILITIES)
        for (n in listOf("Jio", "Airtel", "Vodafone Idea", "BSNL", "MTNL", "ACT Fibernet", "Hathway", "Tata Play", "Dish TV", "d2h", "Sun Direct", "JioFiber", "Airtel Xstream", "Excitel", "Tikona", "YOU Broadband", "Spectranet", "Den Networks", "Siti Cable")) put(n, MOBILE)
        for (n in listOf("LIC", "HDFC Life", "ICICI Prudential", "SBI Life", "Max Life", "Bajaj Allianz", "Tata AIA", "Digit Insurance", "Acko", "Policybazaar", "New India Assurance", "Kotak Life", "PNB MetLife", "Aditya Birla Sun Life",
            "Reliance General Insurance", "Star Health", "Niva Bupa", "Care Health Insurance")) put(n, INSURANCE)
    }

    private fun m(display: String, category: String?, vararg aliases: String) = Merchant(display, category, (listOf(display.lowercase()) + aliases).distinct())
    private fun kw(category: String?, vararg words: String, home: String? = null) = Merchant(null, category, words.toList(), home)
    /**
     * A merchant that is new with the newer categories: [category] is the coarse starter it falls back to for a person without [home] (null: no suggestion for them).
     * Decision (review): a NEW merchant with a coarse fallback (Apple Services, ChatGPT, Google One -> Fun; a petrol pump -> Transport) gives a person without the newer category a
     * suggestion where there was none before. That is accepted: it is only a suggestion on a card in To review, nothing is filed without a tap, and existing entries and reports are untouched.
     * Likewise a person's own category that happens to be named like a newer one ("Fuel") is found by name and suggested for that merchant.
     */
    private fun n(display: String, category: String?, home: String, vararg aliases: String) = Merchant(display, category, (listOf(display.lowercase()) + aliases).distinct(), home)

    /** Every merchant, each with its [Merchant.home] when it has one. */
    val all: List<Merchant> get() = merged

    private val merged: List<Merchant> by lazy { base.map { m -> val h = m.display?.let(HOMES::get); if (h == null || m.home != null) m else Merchant(m.display, m.category, m.aliases, h) } }

    private val base: List<Merchant> = listOf(
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
        kw(BILLS, "electricity", home = UTILITIES), kw(BILLS, "broadband", "recharge", home = MOBILE),
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
        m("Netflix", FUN, "netflix india", "netflix com"), m("Prime Video", FUN, "amazon prime video"), m("Amazon Prime", FUN, "amazon prime membership", "amzn prime"), m("Disney+ Hotstar", FUN, "hotstar", "disney hotstar", "disney plus hotstar", "star india"),
        m("JioHotstar", FUN, "jio hotstar", "jiohotstar"), m("JioCinema", FUN, "jio cinema", "jiocinema"), m("Spotify", FUN, "spotify india"), m("BookMyShow", FUN, "book my show", "bigtree entertainment"), m("PVR", FUN, "pvr inox", "pvr cinemas"),
        m("INOX", FUN, "inox leisure", "inox movies"), m("YouTube Premium", FUN, "youtube premium", "youtube music", "youtubepremium", "google youtubepremium", "google youtube premium"), m("SonyLIV", FUN, "sony liv", "sonyliv"), m("ZEE5", FUN, "zee 5"), kw(FUN, "disney", home = SUBS),
        m("Apple Music", FUN), m("Gaana", FUN), m("JioSaavn", FUN, "jio saavn", "saavn"), m("Wynk Music", FUN, "wynk"), m("Audible", FUN), m("Kindle Unlimited", FUN), m("Xbox", FUN, "xbox game pass"),
        m("PlayStation", FUN, "playstation store", "sony playstation"), m("Dream11", FUN, "dream 11", "sporta technologies"), m("Zupee", FUN), m("Cinepolis", FUN, "cinepolis india"),
        m("Carnival Cinemas", FUN), m("Miraj Cinemas", FUN), m("Paytm Insider", FUN), m("hoichoi", FUN), m("Sun NXT", FUN, "sunnxt"), m("Voot", FUN), m("MX Player", FUN, "mxplayer"),
        m("Eros Now", FUN, "erosnow"), m("ALTBalaji", FUN, "alt balaji"), m("Apple TV", FUN, "apple tv plus"), m("Crunchyroll", FUN), m("Hungama", FUN, "hungama play"),
        m("iCloud", FUN, "icloud storage"), m("Google Play", FUN, "google play store", "google play music", "google play games"), m("Wonderla", FUN, "wonderla holidays"), m("Imagica", FUN, "adlabs imagica"),
        m("Ramoji Film City", FUN), m("Nintendo", FUN, "nintendo eshop"),
       
        // ---- The newer categories: services and merchants that are new, with the coarse starter they fall back to for a person who has not got the category (see [HOMES] for the existing merchants that moved)
        // Subscriptions: apps and software billed every month or year (the bank strings are the ones cards show: APPLE.COM/BILL, GOOGLE *YouTubePremium, NETFLIX.COM, OPENAI *CHATGPT SUBSCR)
        n("Apple Services", FUN, SUBS, "apple com bill", "apple services", "itunes", "itunes com bill", "app store", "applecombill"), n("Google One", FUN, SUBS, "google storage", "google one storage", "google google one"),
        n("Microsoft 365", FUN, SUBS, "office 365", "microsoft office", "microsoft family", "msft microsoft 365"), n("Adobe", FUN, SUBS, "adobe systems", "adobe creative cloud", "adobe inc", "adobe systems software"),
        n("ChatGPT", FUN, SUBS, "openai", "openai llc", "chat gpt", "chatgpt subscription", "chatgpt subscr", "openai chatgpt", "openai chatgpt subscr"), n("Canva", FUN, SUBS, "canva pty"), n("Notion", FUN, SUBS, "notion labs"),
        n("Dropbox", FUN, SUBS), n("LinkedIn Premium", FUN, SUBS, "linkedin", "linkedin corporation"), n("Duolingo", FUN, SUBS, "duolingo plus", "duolingo super"), n("Amazon Music", FUN, SUBS, "amazon music unlimited"),
        n("Times Prime", FUN, SUBS, "timesprime"), n("Lionsgate Play", FUN, SUBS, "lionsgate"), n("Discovery Plus", FUN, SUBS), n("Aha Video", FUN, SUBS),
        // Fuel and travel
        n("Hindustan Petroleum Pump", TRANSPORT, FUEL, "petrol pump", "fuel station", "petrol bunk"),
        n("Booking.com", null, TRAVEL, "booking com", "bookingcom"), n("Airbnb", null, TRAVEL), n("OYO", null, TRAVEL, "oyo rooms"), n("Agoda", null, TRAVEL), n("Treebo", null, TRAVEL, "treebo hotels"), n("FabHotels", null, TRAVEL, "fab hotels"),
        n("Yatra", null, TRAVEL, "yatra online"), n("Thomas Cook", null, TRAVEL, "thomas cook india"), n("SOTC Travel", null, TRAVEL, "sotc"), n("Taj Hotels", null, TRAVEL, "indian hotels company", "taj hotel"),
        n("Marriott", null, TRAVEL, "marriott hotels"), n("Lemon Tree Hotels", null, TRAVEL, "lemon tree"), n("IXIGO Trains", TRANSPORT, TRAVEL, "ixigo trains"),
        // Education
        n("BYJU'S", null, EDUCATION, "byjus", "think and learn"), n("Unacademy", null, EDUCATION), n("Vedantu", null, EDUCATION), n("upGrad", null, EDUCATION), n("Coursera", null, EDUCATION), n("Udemy", null, EDUCATION),
        n("Physics Wallah", null, EDUCATION, "physicswallah"), n("Simplilearn", null, EDUCATION), n("Great Learning", null, EDUCATION, "greatlearning"), n("Scaler", null, EDUCATION, "scaler academy"), n("Toppr", null, EDUCATION),
        n("Testbook", null, EDUCATION), n("Allen Career Institute", null, EDUCATION, "allen career"), n("Aakash Institute", null, EDUCATION, "aakash educational"),
        // Investments
        n("Zerodha", null, INVEST, "zerodha broking", "zerodha coin"), n("Groww", null, INVEST, "nextbillion technology"), n("Upstox", null, INVEST, "rksv securities"), n("Kuvera", null, INVEST), n("ET Money", null, INVEST, "etmoney"),
        n("INDmoney", null, INVEST, "ind money"), n("Paytm Money", null, INVEST), n("Smallcase", null, INVEST), n("Angel One", null, INVEST, "angel broking"), n("5paisa", null, INVEST, "5 paisa"),
        kw(null, "mutual fund", "nps trust", home = INVEST),
        // Loan EMI
        n("Bajaj Finance", null, LOANS, "bajaj finserv", "bajaj finance limited"), n("Tata Capital", null, LOANS), n("HDB Financial", null, LOANS, "hdb financial services"), n("Fullerton India", null, LOANS),
        n("Muthoot Finance", null, LOANS), n("Manappuram Finance", null, LOANS, "manappuram"), n("LIC Housing Finance", null, LOANS, "lic housing"), n("Poonawalla Fincorp", null, LOANS), n("KreditBee", null, LOANS),
        n("Shriram Finance", null, LOANS), n("Mahindra Finance", null, LOANS, "mahindra and mahindra financial"), n("Hero FinCorp", null, LOANS), n("TVS Credit", null, LOANS), n("IIFL Finance", null, LOANS),
        kw(null, "emi", "home loan", "car loan", "personal loan", home = LOANS),
        // Taxes
        n("Income Tax Department", null, TAXES, "income tax", "incometax", "income tax dept"), n("NSDL e-Gov", null, TAXES, "nsdl egov", "tin nsdl", "protean egov"), n("GST Network", null, TAXES, "gstn", "gst payment"),
        kw(null, "property tax", "advance tax", "professional tax", "road tax", home = TAXES),
        // Gifts and donations
        n("Ketto", null, GIVING), n("Milaap", null, GIVING), n("GiveIndia", null, GIVING, "give india"), n("Akshaya Patra", null, GIVING, "akshaya patra foundation"), n("CRY", null, GIVING, "child rights and you"),
        n("Smile Foundation", null, GIVING), n("Tirumala Tirupati Devasthanams", null, GIVING, "ttd", "tirupati devasthanam"), n("ISKCON", null, GIVING), n("Siddhivinayak Temple", null, GIVING, "shree siddhivinayak"),
        // Personal care
        n("Urban Company", null, CARE, "urbanclap", "urban clap"), n("Lakme Salon", null, CARE, "lakme"), n("Naturals Salon", null, CARE), n("Jawed Habib", null, CARE), n("Green Trends", null, CARE),
        n("VLCC", null, CARE), n("Kaya Clinic", null, CARE, "kaya skin clinic"), n("Looks Salon", null, CARE), n("Toni & Guy", null, CARE, "toni and guy"),
        // Rent and housing society
        n("NoBrokerHood", null, RENT, "nobroker hood"), n("MyGate", null, RENT, "my gate"), n("ApnaComplex", null, RENT, "apna complex"), n("NoBroker", null, RENT, "nobroker rent"), kw(null, "house rent", "society maintenance", "rent payment", home = RENT),

        // ---- Display only: payment apps, a clean name and no category
        m("Paytm", null, "paytm wallet", "one97 communications"), m("PhonePe", null, "phone pe", "phonepe wallet"), m("Google Pay", null, "gpay", "google pay india", "google india digital"),
    )
}
