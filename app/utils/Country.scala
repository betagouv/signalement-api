package utils

import play.api.libs.json._
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

sealed case class Country(
    code: CountryCode,
    name: String,
    englishName: String,
    european: Boolean = false,
    transfer: Boolean = false
)

object Country {

  val Afghanistan       = Country(CountryCode("AF"), "Afghanistan", "Afghanistan")
  val AfriqueDuSud      = Country(CountryCode("ZA"), "Afrique du Sud", "South Africa")
  val Albanie           = Country(CountryCode("AL"), "Albanie", "Albania")
  val Algerie           = Country(CountryCode("DZ"), "Algerie", "Algeria")
  val Allemagne         = Country(CountryCode("DE"), "Allemagne", "Germany", european = true)
  val Andorre           = Country(CountryCode("AD"), "Andorre", "Andorra", transfer = true)
  val Angola            = Country(CountryCode("AO"), "Angola", "Angola")
  val AntiguaEtBarbuda  = Country(CountryCode("AG"), "Antigua-et-Barbuda", "Antigua and Barbuda")
  val ArabieSaoudite    = Country(CountryCode("SA"), "Arabie saoudite", "Saudi Arabia")
  val Argentine         = Country(CountryCode("AR"), "Argentine", "Argentina")
  val Armenie           = Country(CountryCode("AM"), "Arménie", "Armenia")
  val Australie         = Country(CountryCode("AU"), "Australie", "Australia")
  val Autriche          = Country(CountryCode("AT"), "Autriche", "Austria", european = true)
  val Azerbaidjan       = Country(CountryCode("AZ"), "Azerbaïdjan", "Azerbaijan")
  val Bahamas           = Country(CountryCode("BS"), "Bahamas", "The Bahamas")
  val Bahrein           = Country(CountryCode("BH"), "Bahreïn", "Bahrain")
  val Bangladesh        = Country(CountryCode("BD"), "Bangladesh", "Bangladesh")
  val Barbade           = Country(CountryCode("BB"), "Barbade", "Barbados")
  val Belgique          = Country(CountryCode("BE"), "Belgique", "Belgium", european = true)
  val Belize            = Country(CountryCode("BZ"), "Bélize", "Belize")
  val Benin             = Country(CountryCode("BJ"), "Bénin", "Benin")
  val Bhoutan           = Country(CountryCode("BT"), "Bhoutan", "Bhutan")
  val Bielorussie       = Country(CountryCode("BY"), "Biélorussie", "Belarus")
  val Birmanie          = Country(CountryCode("MM"), "Birmanie", "Myanmar")
  val Bolivie           = Country(CountryCode("BO"), "Bolivie", "Bolivia")
  val BosnieHerzegovine = Country(CountryCode("BA"), "Bosnie-Herzégovine", "Bosnia and Herzegovina")
  val Botswana          = Country(CountryCode("BW"), "Botswana", "Botswana")
  val Bresil            = Country(CountryCode("BR"), "Brésil", "Brazil")
  val Brunei            = Country(CountryCode("BN"), "Brunei", "Brunei")
  val Bulgarie          = Country(CountryCode("BG"), "Bulgarie", "Bulgaria", european = true)
  val Burkina           = Country(CountryCode("BF"), "Burkina", "Burkina Faso")
  val Burundi           = Country(CountryCode("BI"), "Burundi", "Burundi")
  val Cambodge          = Country(CountryCode("KH"), "Cambodge", "Cambodia")
  val Cameroun          = Country(CountryCode("CM"), "Cameroun", "Cameroon")
  val Canada            = Country(CountryCode("CA"), "Canada", "Canada")
  val CapVert           = Country(CountryCode("CV"), "Cap-Vert", "Cape Verde")
  val Centrafrique      = Country(CountryCode("CF"), "Centrafrique", "Central African Republic")
  val Chili             = Country(CountryCode("CL"), "Chili", "Chile")
  val Chine             = Country(CountryCode("CN"), "Chine", "China")
  val Chypre            = Country(CountryCode("CY"), "Chypre", "Cyprus", european = true)
  val Colombie          = Country(CountryCode("CO"), "Colombie", "Colombia")
  val Comores           = Country(CountryCode("KM"), "Comores", "Comoros")
  val Congo             = Country(CountryCode("CG"), "Congo", "Congo")
  val RepubliqueDemocratiqueDuCongo =
    Country(CountryCode("CD"), "République démocratique du Congo", "Democratic Republic of the Congo")
  val IlesCook                = Country(CountryCode("CK"), "Îles Cook", "Cook Islands")
  val CoreeDuNord             = Country(CountryCode("KP"), "Corée du Nord", "North Korea")
  val CoreeDuSud              = Country(CountryCode("KR"), "Corée du Sud", "South Korea")
  val CostaRica               = Country(CountryCode("CR"), "Costa Rica", "Costa Rica")
  val CoteDIvoire             = Country(CountryCode("CI"), "Côte d'Ivoire", "Côte d'Ivoire")
  val Croatie                 = Country(CountryCode("HR"), "Croatie", "Croatia", european = true)
  val Cuba                    = Country(CountryCode("CU"), "Cuba", "Cuba")
  val Danemark                = Country(CountryCode("DK"), "Danemark", "Denmark", european = true)
  val Djibouti                = Country(CountryCode("DJ"), "Djibouti", "Djibouti")
  val RepubliqueDominicaine   = Country(CountryCode("DO"), "République dominicaine", "Dominican Republic")
  val Dominique               = Country(CountryCode("DM"), "Dominique", "Dominica")
  val Egypte                  = Country(CountryCode("EG"), "Égypte", "Egypt")
  val EmiratsArabesUnis       = Country(CountryCode("AE"), "Émirats arabes unis", "United Arab Emirates")
  val Equateur                = Country(CountryCode("EC"), "Équateur", "Ecuador")
  val Erythree                = Country(CountryCode("ER"), "Érythrée", "Eritrea")
  val Espagne                 = Country(CountryCode("ES"), "Espagne", "Spain", european = true)
  val Estonie                 = Country(CountryCode("EE"), "Estonie", "Estonia", european = true)
  val Eswatini                = Country(CountryCode("SZ"), "Eswatini", "Eswatini")
  val EtatsUnis               = Country(CountryCode("US"), "États-Unis", "United States")
  val Ethiopie                = Country(CountryCode("ET"), "Éthiopie", "Ethiopia")
  val Fidji                   = Country(CountryCode("FJ"), "Fidji", "Fiji")
  val Finlande                = Country(CountryCode("FI"), "Finlande", "Finland", european = true)
  val France                  = Country(CountryCode("FR"), "France", "France", european = true)
  val Gabon                   = Country(CountryCode("GA"), "Gabon", "Gabon")
  val Gambie                  = Country(CountryCode("GM"), "Gambie", "Gambia")
  val Georgie                 = Country(CountryCode("GE"), "Géorgie", "Georgia")
  val Ghana                   = Country(CountryCode("GH"), "Ghana", "Ghana")
  val Grece                   = Country(CountryCode("GR"), "Grèce", "Greece", european = true)
  val Grenade                 = Country(CountryCode("GD"), "Grenade", "Grenada")
  val Guatemala               = Country(CountryCode("GT"), "Guatémala", "Guatemala")
  val Guinee                  = Country(CountryCode("GN"), "Guinée", "Guinea")
  val GuineeEquatoriale       = Country(CountryCode("GQ"), "Guinée équatoriale", "Equatorial Guinea")
  val GuineeBissao            = Country(CountryCode("GW"), "Guinée-Bissao", "Guinea-Bissau")
  val Guyana                  = Country(CountryCode("GY"), "Guyana", "Guyana")
  val Haiti                   = Country(CountryCode("HT"), "Haïti", "Haiti")
  val Honduras                = Country(CountryCode("HN"), "Honduras", "Honduras")
  val Hongrie                 = Country(CountryCode("HU"), "Hongrie", "Hungary", european = true)
  val Inde                    = Country(CountryCode("IN"), "Inde", "India")
  val Indonesie               = Country(CountryCode("ID"), "Indonésie", "Indonesia")
  val Irak                    = Country(CountryCode("IQ"), "Irak", "Iraq")
  val Iran                    = Country(CountryCode("IR"), "Iran", "Iran")
  val Irlande                 = Country(CountryCode("IE"), "Irlande", "Ireland", european = true)
  val Islande                 = Country(CountryCode("IS"), "Islande", "Iceland", european = true)
  val Israel                  = Country(CountryCode("IL"), "Israël", "Israel")
  val Italie                  = Country(CountryCode("IT"), "Italie", "Italy", european = true)
  val Jamaique                = Country(CountryCode("JM"), "Jamaïque", "Jamaica")
  val Japon                   = Country(CountryCode("JP"), "Japon", "Japan")
  val Jordanie                = Country(CountryCode("JO"), "Jordanie", "Jordan")
  val Kazakhstan              = Country(CountryCode("KZ"), "Kazakhstan", "Kazakhstan")
  val Kenya                   = Country(CountryCode("KE"), "Kénya", "Kenya")
  val Kirghizstan             = Country(CountryCode("KG"), "Kirghizstan", "Kyrgyzstan")
  val Kiribati                = Country(CountryCode("KI"), "Kiribati", "Kiribati")
  val Kosovo                  = Country(CountryCode("XK"), "Kosovo", "Kosovo")
  val Koweit                  = Country(CountryCode("KW"), "Koweït", "Kuwait")
  val Laos                    = Country(CountryCode("LA"), "Laos", "Laos")
  val Lesotho                 = Country(CountryCode("LS"), "Lésotho", "Lesotho")
  val Lettonie                = Country(CountryCode("LV"), "Lettonie", "Latvia", european = true)
  val Liban                   = Country(CountryCode("LB"), "Liban", "Lebanon")
  val Liberia                 = Country(CountryCode("LR"), "Libéria", "Liberia")
  val Libye                   = Country(CountryCode("LY"), "Libye", "Libya")
  val Liechtenstein           = Country(CountryCode("LI"), "Liechtenstein", "Liechtenstein")
  val Lituanie                = Country(CountryCode("LT"), "Lituanie", "Lithuania", european = true)
  val Luxembourg              = Country(CountryCode("LU"), "Luxembourg", "Luxembourg", european = true)
  val MacedoineDuNord         = Country(CountryCode("MK"), "Macédoine du Nord", "North Macedonia")
  val Madagascar              = Country(CountryCode("MG"), "Madagascar", "Madagascar")
  val Malaisie                = Country(CountryCode("MY"), "Malaisie", "Malaysia")
  val Malawi                  = Country(CountryCode("MW"), "Malawi", "Malawi")
  val Maldives                = Country(CountryCode("MV"), "Maldives", "Maldives")
  val Mali                    = Country(CountryCode("ML"), "Mali", "Mali")
  val Malte                   = Country(CountryCode("MT"), "Malte", "Malta", european = true)
  val Maroc                   = Country(CountryCode("MA"), "Maroc", "Morocco")
  val IlesMarshall            = Country(CountryCode("MH"), "Îles Marshall", "Marshall Islands")
  val Maurice                 = Country(CountryCode("MU"), "Maurice", "Mauritius")
  val Mauritanie              = Country(CountryCode("MR"), "Mauritanie", "Mauritania")
  val Mexique                 = Country(CountryCode("MX"), "Mexique", "Mexico")
  val Micronesie              = Country(CountryCode("FM"), "Micronésie", "Micronesia")
  val Moldavie                = Country(CountryCode("MD"), "Moldavie", "Moldova")
  val Monaco                  = Country(CountryCode("MC"), "Monaco", "Monaco")
  val Mongolie                = Country(CountryCode("MN"), "Mongolie", "Mongolia")
  val Montenegro              = Country(CountryCode("ME"), "Monténégro", "Montenegro")
  val Mozambique              = Country(CountryCode("MZ"), "Mozambique", "Mozambique")
  val Namibie                 = Country(CountryCode("NA"), "Namibie", "Namibia")
  val Nauru                   = Country(CountryCode("NR"), "Nauru", "Nauru")
  val Nepal                   = Country(CountryCode("NP"), "Népal", "Nepal")
  val Nicaragua               = Country(CountryCode("NI"), "Nicaragua", "Nicaragua")
  val Niger                   = Country(CountryCode("NE"), "Niger", "Niger")
  val Nigeria                 = Country(CountryCode("NG"), "Nigéria", "Nigeria")
  val Niue                    = Country(CountryCode("NU"), "Niue", "Niue")
  val Norvege                 = Country(CountryCode("NO"), "Norvège", "Norway", european = true)
  val NouvelleZelande         = Country(CountryCode("NZ"), "Nouvelle-Zélande", "New Zealand")
  val Oman                    = Country(CountryCode("OM"), "Oman", "Oman")
  val Ouganda                 = Country(CountryCode("UG"), "Ouganda", "Uganda")
  val Ouzbekistan             = Country(CountryCode("UZ"), "Ouzbékistan", "Uzbekistan")
  val Pakistan                = Country(CountryCode("PK"), "Pakistan", "Pakistan")
  val Palaos                  = Country(CountryCode("PW"), "Palaos", "Palau")
  val Panama                  = Country(CountryCode("PA"), "Panama", "Panama")
  val PapouasieNouvelleGuinee = Country(CountryCode("PG"), "Papouasie-Nouvelle-Guinée", "Papua New Guinea")
  val Paraguay                = Country(CountryCode("PY"), "Paraguay", "Paraguay")
  val PaysBas                 = Country(CountryCode("NL"), "Pays-Bas", "Netherlands", european = true)
  val Perou                   = Country(CountryCode("PE"), "Pérou", "Peru")
  val Philippines             = Country(CountryCode("PH"), "Philippines", "Philippines")
  val Pologne                 = Country(CountryCode("PL"), "Pologne", "Poland", european = true)
  val Portugal                = Country(CountryCode("PT"), "Portugal", "Portugal", european = true)
  val Qatar                   = Country(CountryCode("QA"), "Qatar", "Qatar")
  val Roumanie                = Country(CountryCode("RO"), "Roumanie", "Romania", european = true)
  val RoyaumeUni              = Country(CountryCode("GB"), "Royaume-Uni", "United Kingdom")
  val Russie                  = Country(CountryCode("RU"), "Russie", "Russia")
  val Rwanda                  = Country(CountryCode("RW"), "Rwanda", "Rwanda")
  val SaintChristopheEtNieves = Country(CountryCode("KN"), "Saint-Christophe-et-Niévès", "Saint Kitts and Nevis")
  val SainteLucie             = Country(CountryCode("LC"), "Sainte-Lucie", "Saint Lucia")
  val SaintMarin              = Country(CountryCode("SM"), "Saint-Marin", "San Marino")
  val SaintVincentEtLesGrenadines =
    Country(CountryCode("VC"), "Saint-Vincent-et-les-Grenadines", "Saint Vincent and the Grenadines")
  val Salomon           = Country(CountryCode("SB"), "Salomon", "Solomon Islands")
  val Salvador          = Country(CountryCode("SV"), "Salvador", "El Salvador")
  val Samoa             = Country(CountryCode("WS"), "Samoa", "Samoa")
  val SaoTomeEtPrincipe = Country(CountryCode("ST"), "Sao Tomé-et-Principe", "Sao Tome and Principe")
  val Senegal           = Country(CountryCode("SN"), "Sénégal", "Senegal")
  val Serbie            = Country(CountryCode("RS"), "Serbie", "Serbia")
  val Seychelles        = Country(CountryCode("SC"), "Seychelles", "Seychelles")
  val SierraLeone       = Country(CountryCode("SL"), "Sierra Leone", "Sierra Leone")
  val Singapour         = Country(CountryCode("SG"), "Singapour", "Singapore")
  val Slovaquie         = Country(CountryCode("SK"), "Slovaquie", "Slovakia", european = true)
  val Slovenie          = Country(CountryCode("SI"), "Slovénie", "Slovenia", european = true)
  val Somalie           = Country(CountryCode("SO"), "Somalie", "Somalia")
  val Soudan            = Country(CountryCode("SD"), "Soudan", "Sudan")
  val SoudanDuSud       = Country(CountryCode("SS"), "Soudan du Sud", "South Sudan")
  val SriLanka          = Country(CountryCode("LK"), "Sri Lanka", "Sri Lanka")
  val Suede             = Country(CountryCode("SE"), "Suède", "Sweden", european = true)
  val Suisse            = Country(CountryCode("CH"), "Suisse", "Switzerland", transfer = true)
  val Suriname          = Country(CountryCode("SR"), "Suriname", "Suriname")
  val Syrie             = Country(CountryCode("SY"), "Syrie", "Syria")
  val Tadjikistan       = Country(CountryCode("TJ"), "Tadjikistan", "Tajikistan")
  val Tanzanie          = Country(CountryCode("TZ"), "Tanzanie", "Tanzania")
  val Tchad             = Country(CountryCode("TD"), "Tchad", "Chad")
  val Tchequie          = Country(CountryCode("CZ"), "Tchéquie", "Czech Republic", european = true)
  val Thailande         = Country(CountryCode("TH"), "Thaïlande", "Thailand")
  val TimorOriental     = Country(CountryCode("TL"), "Timor oriental", "East Timor")
  val Togo              = Country(CountryCode("TG"), "Togo", "Togo")
  val Tonga             = Country(CountryCode("TO"), "Tonga", "Tonga")
  val TriniteEtTobago   = Country(CountryCode("TT"), "Trinité-et-Tobago", "Trinidad and Tobago")
  val Tunisie           = Country(CountryCode("TN"), "Tunisie", "Tunisia")
  val Turkmenistan      = Country(CountryCode("TM"), "Turkménistan", "Turkmenistan")
  val Turquie           = Country(CountryCode("TR"), "Turquie", "Turkey")
  val Tuvalu            = Country(CountryCode("TV"), "Tuvalu", "Tuvalu")
  val Ukraine           = Country(CountryCode("UA"), "Ukraine", "Ukraine")
  val Uruguay           = Country(CountryCode("UY"), "Uruguay", "Uruguay")
  val Vanuatu           = Country(CountryCode("VU"), "Vanuatu", "Vanuatu")
  val Vatican           = Country(CountryCode("VA"), "Vatican", "Vatican City")
  val Venezuela         = Country(CountryCode("VE"), "Vénézuéla", "Venezuela")
  val Vietnam           = Country(CountryCode("VN"), "Vietnam", "Vietnam")
  val Yemen             = Country(CountryCode("YE"), "Yémen", "Yemen")
  val Zambie            = Country(CountryCode("ZM"), "Zambie", "Zambia")
  val Zimbabwe          = Country(CountryCode("ZW"), "Zimbabwé", "Zimbabwe")
  val IlesFeroe         = Country(CountryCode("FO"), "Îles Féroé", "Faroe Islands")
  val Svalbard          = Country(CountryCode("SJ"), "Svalbard et Île Jan Mayen", "Svalbard and Jan Mayen")
  val IleBouvet         = Country(CountryCode("BV"), "Île Bouvet", "Bouvet Island")
  val Jersey            = Country(CountryCode("JE"), "Jersey", "Jersey")
  val IleMan            = Country(CountryCode("IM"), "Île Man", "Isle of Man")
  val Guernsey          = Country(CountryCode("GG"), "Guernsey", "Bailiwick of Guernsey")
  val Gibraltar         = Country(CountryCode("GI"), "Gibraltar", "Gibraltar")
  val Aruba             = Country(CountryCode("AW"), "Aruba", "Aruba")
  val HongKong          = Country(CountryCode("HK"), "Hong-Kong", "Hong Kong")
  val Macao             = Country(CountryCode("MO"), "Macao", "Macau")
  val Taiwan            = Country(CountryCode("TW"), "Taiwan", "Taiwan")
  val Palestine         = Country(CountryCode("PS"), "État de Palestine", "State of Palestine")
  val SainteHelene =
    Country(
      CountryCode("SH"),
      "Sainte-Hélène, Ascension et Tristan da Cunha",
      "Saint Helena, Ascension and Tristan da Cunha"
    )
  val TerritoireBritannique =
    Country(CountryCode("IO"), "Territoire britannique de l'océan Indien", "British Indian Ocean Territory")
  val SaharaOccidental        = Country(CountryCode("EH"), "Sahara occidental", "Western Sahara")
  val IlesViergesBritanniques = Country(CountryCode("VG"), "Îles Vierges britanniques", "British Virgin Islands")
  val IlesTurques             = Country(CountryCode("TC"), "Îles Turques-et-Caïques", "Turks and Caicos Islands")
  val Montserrat              = Country(CountryCode("MS"), "Montserrat", "Montserrat")
  val IlesCaimans             = Country(CountryCode("KY"), "Îles Caïmans", "Cayman Islands")
  val Bermudes                = Country(CountryCode("BM"), "Bermudes", "Bermuda")
  val Anguilla                = Country(CountryCode("AI"), "Anguilla", "Anguilla")
  val GeorgieDuSud =
    Country(
      CountryCode("GS"),
      "Géorgie du Sud-et-les îles Sandwich du Sud",
      "South Georgia and the South Sandwich Islands"
    )
  val Falkland              = Country(CountryCode("FK"), "Îles Malouines ou Falkland", "Falkland Islands")
  val Groenland             = Country(CountryCode("GL"), "Groenland", "Greenland")
  val AntillesNeerlandaises = Country(CountryCode("AN"), "Antilles néerlandaises", "Netherlands Antilles")
  val IlesVierges           = Country(CountryCode("VI"), "Îles Vierges des États-Unis", "United States Virgin Islands")
  val PortoRico             = Country(CountryCode("PR"), "Porto Rico", "Puerto Rico")
  val Bonaire       = Country(CountryCode("BQ"), "Bonaire, Saint Eustache et Saba", "Bonaire, Sint Eustatius and Saba")
  val Curacao       = Country(CountryCode("CW"), "Curaçao", "Curaçao")
  val SaintMartinNL = Country(CountryCode("SX"), "Saint-Martin (royaume des Pays-Bas)", "Sint Maarten (Dutch part)")
  val Norfolk       = Country(CountryCode("NF"), "Île Norfolk", "Norfolk Island")
  val MacDo         = Country(CountryCode("HM"), "Îles Heard-et-MacDonald", "Heard Island and McDonald Islands")
  val Christmas     = Country(CountryCode("CX"), "Île Christmas", "Christmas Island")
  val IlesCocos     = Country(CountryCode("CC"), "Îles Cocos", "Cocos (Keeling) Islands")
  val Tokelau       = Country(CountryCode("TK"), "Tokelau", "Tokelau")
  val Pitcairn      = Country(CountryCode("PN"), "Îles Pitcairn", "Pitcairn Islands")
  val IlesMariannes = Country(CountryCode("MP"), "Îles Mariannes du Nord", "Northern Mariana Islands")
  val Guam          = Country(CountryCode("GU"), "Guam", "Guam")
  val SamoaAmericaines = Country(CountryCode("AS"), "Samoa américaines", "American Samoa")

  val countries = List(
    Afghanistan,
    AfriqueDuSud,
    Albanie,
    Algerie,
    Allemagne,
    Andorre,
    Angola,
    AntiguaEtBarbuda,
    ArabieSaoudite,
    Argentine,
    Armenie,
    Australie,
    Autriche,
    Azerbaidjan,
    Bahamas,
    Bahrein,
    Bangladesh,
    Barbade,
    Belgique,
    Belize,
    Benin,
    Bhoutan,
    Bielorussie,
    Birmanie,
    Bolivie,
    BosnieHerzegovine,
    Botswana,
    Bresil,
    Brunei,
    Bulgarie,
    Burkina,
    Burundi,
    Cambodge,
    Cameroun,
    Canada,
    CapVert,
    Centrafrique,
    Chili,
    Chine,
    Chypre,
    Colombie,
    Comores,
    Congo,
    RepubliqueDemocratiqueDuCongo,
    IlesCook,
    CoreeDuNord,
    CoreeDuSud,
    CostaRica,
    CoteDIvoire,
    Croatie,
    Cuba,
    Danemark,
    Djibouti,
    RepubliqueDominicaine,
    Dominique,
    Egypte,
    EmiratsArabesUnis,
    Equateur,
    Erythree,
    Espagne,
    Estonie,
    Eswatini,
    EtatsUnis,
    Ethiopie,
    Fidji,
    Finlande,
    France,
    Gabon,
    Gambie,
    Georgie,
    Ghana,
    Grece,
    Grenade,
    Guatemala,
    Guinee,
    GuineeEquatoriale,
    GuineeBissao,
    Guyana,
    Haiti,
    Honduras,
    Hongrie,
    Inde,
    Indonesie,
    Irak,
    Iran,
    Irlande,
    Islande,
    Israel,
    Italie,
    Jamaique,
    Japon,
    Jordanie,
    Kazakhstan,
    Kenya,
    Kirghizstan,
    Kiribati,
    Kosovo,
    Koweit,
    Laos,
    Lesotho,
    Lettonie,
    Liban,
    Liberia,
    Libye,
    Liechtenstein,
    Lituanie,
    Luxembourg,
    MacedoineDuNord,
    Madagascar,
    Malaisie,
    Malawi,
    Maldives,
    Mali,
    Malte,
    Maroc,
    IlesMarshall,
    Maurice,
    Mauritanie,
    Mexique,
    Micronesie,
    Moldavie,
    Monaco,
    Mongolie,
    Montenegro,
    Mozambique,
    Namibie,
    Nauru,
    Nepal,
    Nicaragua,
    Niger,
    Nigeria,
    Niue,
    Norvege,
    NouvelleZelande,
    Oman,
    Ouganda,
    Ouzbekistan,
    Pakistan,
    Palaos,
    Panama,
    PapouasieNouvelleGuinee,
    Paraguay,
    PaysBas,
    Perou,
    Philippines,
    Pologne,
    Portugal,
    Qatar,
    Roumanie,
    RoyaumeUni,
    Russie,
    Rwanda,
    SaintChristopheEtNieves,
    SainteLucie,
    SaintMarin,
    SaintVincentEtLesGrenadines,
    Salomon,
    Salvador,
    Samoa,
    SaoTomeEtPrincipe,
    Senegal,
    Serbie,
    Seychelles,
    SierraLeone,
    Singapour,
    Slovaquie,
    Slovenie,
    Somalie,
    Soudan,
    SoudanDuSud,
    SriLanka,
    Suede,
    Suisse,
    Suriname,
    Syrie,
    Tadjikistan,
    Tanzanie,
    Tchad,
    Tchequie,
    Thailande,
    TimorOriental,
    Togo,
    Tonga,
    TriniteEtTobago,
    Tunisie,
    Turkmenistan,
    Turquie,
    Tuvalu,
    Ukraine,
    Uruguay,
    Vanuatu,
    Vatican,
    Venezuela,
    Vietnam,
    Yemen,
    Zambie,
    Zimbabwe,
    IlesFeroe,
    Svalbard,
    IleBouvet,
    Jersey,
    IleMan,
    Guernsey,
    Gibraltar,
    Aruba,
    HongKong,
    Macao,
    Taiwan,
    Palestine,
    SainteHelene,
    TerritoireBritannique,
    SaharaOccidental,
    IlesViergesBritanniques,
    IlesTurques,
    Montserrat,
    IlesCaimans,
    Bermudes,
    Anguilla,
    GeorgieDuSud,
    Falkland,
    Groenland,
    AntillesNeerlandaises,
    IlesVierges,
    PortoRico,
    Bonaire,
    Curacao,
    SaintMartinNL,
    Norfolk,
    MacDo,
    Christmas,
    IlesCocos,
    Tokelau,
    Pitcairn,
    IlesMariannes,
    Guam,
    SamoaAmericaines
  )

  private val countriesMap: Map[CountryCode, Country] = countries.map(country => country.code -> country).toMap

  def fromCode(code: String) = countriesMap(CountryCode(code))

  implicit val reads: Reads[Country]    = Reads.StringReads.map(c => fromCode(c))
  implicit val writes: OWrites[Country] = Json.writes[Country]

  implicit val CountryColumnType: JdbcType[Country] with BaseTypedType[Country] =
    MappedColumnType.base[Country, String](_.code.value, s => Country.fromCode(s))

  implicit val countryListColumnType: JdbcType[List[Country]] with BaseTypedType[List[Country]] =
    MappedColumnType.base[List[Country], List[String]](
      _.map(_.code.value),
      _.map(s => Country.fromCode(s))
    )
}
