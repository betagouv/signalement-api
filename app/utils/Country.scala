package utils

import play.api.libs.json._
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

sealed case class Country(
    code: String,
    name: String,
    englishName: String,
    european: Boolean = false,
    transfer: Boolean = false
)

object Country {

  val Afghanistan       = Country("AF", "Afghanistan", "Afghanistan")
  val AfriqueDuSud      = Country("ZA", "Afrique du Sud", "South Africa")
  val Albanie           = Country("AL", "Albanie", "Albania")
  val Algerie           = Country("DZ", "Algerie", "Algeria")
  val Allemagne         = Country("DE", "Allemagne", "Germany", european = true)
  val Andorre           = Country("AD", "Andorre", "Andorra", transfer = true)
  val Angola            = Country("AO", "Angola", "Angola")
  val AntiguaEtBarbuda  = Country("AG", "Antigua-et-Barbuda", "Antigua and Barbuda")
  val ArabieSaoudite    = Country("SA", "Arabie saoudite", "Saudi Arabia")
  val Argentine         = Country("AR", "Argentine", "Argentina")
  val Armenie           = Country("AM", "Arménie", "Armenia")
  val Australie         = Country("AU", "Australie", "Australia")
  val Autriche          = Country("AT", "Autriche", "Austria", european = true)
  val Azerbaidjan       = Country("AZ", "Azerbaïdjan", "Azerbaijan")
  val Bahamas           = Country("BS", "Bahamas", "The Bahamas")
  val Bahrein           = Country("BH", "Bahreïn", "Bahrain")
  val Bangladesh        = Country("BD", "Bangladesh", "Bangladesh")
  val Barbade           = Country("BB", "Barbade", "Barbados")
  val Belgique          = Country("BE", "Belgique", "Belgium", european = true)
  val Belize            = Country("BZ", "Bélize", "Belize")
  val Benin             = Country("BJ", "Bénin", "Benin")
  val Bhoutan           = Country("BT", "Bhoutan", "Bhutan")
  val Bielorussie       = Country("BY", "Biélorussie", "Belarus")
  val Birmanie          = Country("MM", "Birmanie", "Myanmar")
  val Bolivie           = Country("BO", "Bolivie", "Bolivia")
  val BosnieHerzegovine = Country("BA", "Bosnie-Herzégovine", "Bosnia and Herzegovina")
  val Botswana          = Country("BW", "Botswana", "Botswana")
  val Bresil            = Country("BR", "Brésil", "Brazil")
  val Brunei            = Country("BN", "Brunei", "Brunei")
  val Bulgarie          = Country("BG", "Bulgarie", "Bulgaria", european = true)
  val Burkina           = Country("BF", "Burkina", "Burkina Faso")
  val Burundi           = Country("BI", "Burundi", "Burundi")
  val Cambodge          = Country("KH", "Cambodge", "Cambodia")
  val Cameroun          = Country("CM", "Cameroun", "Cameroon")
  val Canada            = Country("CA", "Canada", "Canada")
  val CapVert           = Country("CV", "Cap-Vert", "Cape Verde")
  val Centrafrique      = Country("CF", "Centrafrique", "Central African Republic")
  val Chili             = Country("CL", "Chili", "Chile")
  val Chine             = Country("CN", "Chine", "China")
  val Chypre            = Country("CY", "Chypre", "Cyprus", european = true)
  val Colombie          = Country("CO", "Colombie", "Colombia")
  val Comores           = Country("KM", "Comores", "Comoros")
  val Congo             = Country("CG", "Congo", "Congo")
  val RepubliqueDemocratiqueDuCongo =
    Country("CD", "République démocratique du Congo", "Democratic Republic of the Congo")
  val IlesCook                    = Country("CK", "Îles Cook", "Cook Islands")
  val CoreeDuNord                 = Country("KP", "Corée du Nord", "North Korea")
  val CoreeDuSud                  = Country("KR", "Corée du Sud", "South Korea")
  val CostaRica                   = Country("CR", "Costa Rica", "Costa Rica")
  val CoteDIvoire                 = Country("CI", "Côte d'Ivoire", "Côte d'Ivoire")
  val Croatie                     = Country("HR", "Croatie", "Croatia", european = true)
  val Cuba                        = Country("CU", "Cuba", "Cuba")
  val Danemark                    = Country("DK", "Danemark", "Denmark", european = true)
  val Djibouti                    = Country("DJ", "Djibouti", "Djibouti")
  val RepubliqueDominicaine       = Country("DO", "République dominicaine", "Dominican Republic")
  val Dominique                   = Country("DM", "Dominique", "Dominica")
  val Egypte                      = Country("EG", "Égypte", "Egypt")
  val EmiratsArabesUnis           = Country("AE", "Émirats arabes unis", "United Arab Emirates")
  val Equateur                    = Country("EC", "Équateur", "Ecuador")
  val Erythree                    = Country("ER", "Érythrée", "Eritrea")
  val Espagne                     = Country("ES", "Espagne", "Spain", european = true)
  val Estonie                     = Country("EE", "Estonie", "Estonia", european = true)
  val Eswatini                    = Country("SZ", "Eswatini", "Eswatini")
  val EtatsUnis                   = Country("US", "États-Unis", "United States")
  val Ethiopie                    = Country("ET", "Éthiopie", "Ethiopia")
  val Fidji                       = Country("FJ", "Fidji", "Fiji")
  val Finlande                    = Country("FI", "Finlande", "Finland", european = true)
  val France                      = Country("FR", "France", "France", european = true)
  val Gabon                       = Country("GA", "Gabon", "Gabon")
  val Gambie                      = Country("GM", "Gambie", "Gambia")
  val Georgie                     = Country("GE", "Géorgie", "Georgia")
  val Ghana                       = Country("GH", "Ghana", "Ghana")
  val Grece                       = Country("GR", "Grèce", "Greece", european = true)
  val Grenade                     = Country("GD", "Grenade", "Grenada")
  val Guatemala                   = Country("GT", "Guatémala", "Guatemala")
  val Guinee                      = Country("GN", "Guinée", "Guinea")
  val GuineeEquatoriale           = Country("GQ", "Guinée équatoriale", "Equatorial Guinea")
  val GuineeBissao                = Country("GW", "Guinée-Bissao", "Guinea-Bissau")
  val Guyana                      = Country("GY", "Guyana", "Guyana")
  val Haiti                       = Country("HT", "Haïti", "Haiti")
  val Honduras                    = Country("HN", "Honduras", "Honduras")
  val Hongrie                     = Country("HU", "Hongrie", "Hungary", european = true)
  val Inde                        = Country("IN", "Inde", "India")
  val Indonesie                   = Country("ID", "Indonésie", "Indonesia")
  val Irak                        = Country("IQ", "Irak", "Iraq")
  val Iran                        = Country("IR", "Iran", "Iran")
  val Irlande                     = Country("IE", "Irlande", "Ireland", european = true)
  val Islande                     = Country("IS", "Islande", "Iceland", european = true)
  val Israel                      = Country("IL", "Israël", "Israel")
  val Italie                      = Country("IT", "Italie", "Italy", european = true)
  val Jamaique                    = Country("JM", "Jamaïque", "Jamaica")
  val Japon                       = Country("JP", "Japon", "Japan")
  val Jordanie                    = Country("JO", "Jordanie", "Jordan")
  val Kazakhstan                  = Country("KZ", "Kazakhstan", "Kazakhstan")
  val Kenya                       = Country("KE", "Kénya", "Kenya")
  val Kirghizstan                 = Country("KG", "Kirghizstan", "Kyrgyzstan")
  val Kiribati                    = Country("KI", "Kiribati", "Kiribati")
  val Kosovo                      = Country("XK", "Kosovo", "Kosovo")
  val Koweit                      = Country("KW", "Koweït", "Kuwait")
  val Laos                        = Country("LA", "Laos", "Laos")
  val Lesotho                     = Country("LS", "Lésotho", "Lesotho")
  val Lettonie                    = Country("LV", "Lettonie", "Latvia", european = true)
  val Liban                       = Country("LB", "Liban", "Lebanon")
  val Liberia                     = Country("LR", "Libéria", "Liberia")
  val Libye                       = Country("LY", "Libye", "Libya")
  val Liechtenstein               = Country("LI", "Liechtenstein", "Liechtenstein")
  val Lituanie                    = Country("LT", "Lituanie", "Lithuania", european = true)
  val Luxembourg                  = Country("LU", "Luxembourg", "Luxembourg", european = true)
  val MacedoineDuNord             = Country("MK", "Macédoine du Nord", "North Macedonia")
  val Madagascar                  = Country("MG", "Madagascar", "Madagascar")
  val Malaisie                    = Country("MY", "Malaisie", "Malaysia")
  val Malawi                      = Country("MW", "Malawi", "Malawi")
  val Maldives                    = Country("MV", "Maldives", "Maldives")
  val Mali                        = Country("ML", "Mali", "Mali")
  val Malte                       = Country("MT", "Malte", "Malta", european = true)
  val Maroc                       = Country("MA", "Maroc", "Morocco")
  val IlesMarshall                = Country("MH", "Îles Marshall", "Marshall Islands")
  val Maurice                     = Country("MU", "Maurice", "Mauritius")
  val Mauritanie                  = Country("MR", "Mauritanie", "Mauritania")
  val Mexique                     = Country("MX", "Mexique", "Mexico")
  val Micronesie                  = Country("FM", "Micronésie", "Micronesia")
  val Moldavie                    = Country("MD", "Moldavie", "Moldova")
  val Monaco                      = Country("MC", "Monaco", "Monaco")
  val Mongolie                    = Country("MN", "Mongolie", "Mongolia")
  val Montenegro                  = Country("ME", "Monténégro", "Montenegro")
  val Mozambique                  = Country("MZ", "Mozambique", "Mozambique")
  val Namibie                     = Country("NA", "Namibie", "Namibia")
  val Nauru                       = Country("NR", "Nauru", "Nauru")
  val Nepal                       = Country("NP", "Népal", "Nepal")
  val Nicaragua                   = Country("NI", "Nicaragua", "Nicaragua")
  val Niger                       = Country("NE", "Niger", "Niger")
  val Nigeria                     = Country("NG", "Nigéria", "Nigeria")
  val Niue                        = Country("NU", "Niue", "Niue")
  val Norvege                     = Country("NO", "Norvège", "Norway", european = true)
  val NouvelleZelande             = Country("NZ", "Nouvelle-Zélande", "New Zealand")
  val Oman                        = Country("OM", "Oman", "Oman")
  val Ouganda                     = Country("UG", "Ouganda", "Uganda")
  val Ouzbekistan                 = Country("UZ", "Ouzbékistan", "Uzbekistan")
  val Pakistan                    = Country("PK", "Pakistan", "Pakistan")
  val Palaos                      = Country("PW", "Palaos", "Palau")
  val Panama                      = Country("PA", "Panama", "Panama")
  val PapouasieNouvelleGuinee     = Country("PG", "Papouasie-Nouvelle-Guinée", "Papua New Guinea")
  val Paraguay                    = Country("PY", "Paraguay", "Paraguay")
  val PaysBas                     = Country("NL", "Pays-Bas", "Netherlands", european = true)
  val Perou                       = Country("PE", "Pérou", "Peru")
  val Philippines                 = Country("PH", "Philippines", "Philippines")
  val Pologne                     = Country("PL", "Pologne", "Poland", european = true)
  val Portugal                    = Country("PT", "Portugal", "Portugal", european = true)
  val Qatar                       = Country("QA", "Qatar", "Qatar")
  val Roumanie                    = Country("RO", "Roumanie", "Romania", european = true)
  val RoyaumeUni                  = Country("GB", "Royaume-Uni", "United Kingdom")
  val Russie                      = Country("RU", "Russie", "Russia")
  val Rwanda                      = Country("RW", "Rwanda", "Rwanda")
  val SaintChristopheEtNieves     = Country("KN", "Saint-Christophe-et-Niévès", "Saint Kitts and Nevis")
  val SainteLucie                 = Country("LC", "Sainte-Lucie", "Saint Lucia")
  val SaintMarin                  = Country("SM", "Saint-Marin", "San Marino")
  val SaintVincentEtLesGrenadines = Country("VC", "Saint-Vincent-et-les-Grenadines", "Saint Vincent and the Grenadines")
  val Salomon                     = Country("SB", "Salomon", "Solomon Islands")
  val Salvador                    = Country("SV", "Salvador", "El Salvador")
  val Samoa                       = Country("WS", "Samoa", "Samoa")
  val SaoTomeEtPrincipe           = Country("ST", "Sao Tomé-et-Principe", "Sao Tome and Principe")
  val Senegal                     = Country("SN", "Sénégal", "Senegal")
  val Serbie                      = Country("RS", "Serbie", "Serbia")
  val Seychelles                  = Country("SC", "Seychelles", "Seychelles")
  val SierraLeone                 = Country("SL", "Sierra Leone", "Sierra Leone")
  val Singapour                   = Country("SG", "Singapour", "Singapore")
  val Slovaquie                   = Country("SK", "Slovaquie", "Slovakia", european = true)
  val Slovenie                    = Country("SI", "Slovénie", "Slovenia", european = true)
  val Somalie                     = Country("SO", "Somalie", "Somalia")
  val Soudan                      = Country("SD", "Soudan", "Sudan")
  val SoudanDuSud                 = Country("SS", "Soudan du Sud", "South Sudan")
  val SriLanka                    = Country("LK", "Sri Lanka", "Sri Lanka")
  val Suede                       = Country("SE", "Suède", "Sweden", european = true)
  val Suisse                      = Country("CH", "Suisse", "Switzerland", transfer = true)
  val Suriname                    = Country("SR", "Suriname", "Suriname")
  val Syrie                       = Country("SY", "Syrie", "Syria")
  val Tadjikistan                 = Country("TJ", "Tadjikistan", "Tajikistan")
  val Tanzanie                    = Country("TZ", "Tanzanie", "Tanzania")
  val Tchad                       = Country("TD", "Tchad", "Chad")
  val Tchequie                    = Country("CZ", "Tchéquie", "Czech Republic", european = true)
  val Thailande                   = Country("TH", "Thaïlande", "Thailand")
  val TimorOriental               = Country("TL", "Timor oriental", "East Timor")
  val Togo                        = Country("TG", "Togo", "Togo")
  val Tonga                       = Country("TO", "Tonga", "Tonga")
  val TriniteEtTobago             = Country("TT", "Trinité-et-Tobago", "Trinidad and Tobago")
  val Tunisie                     = Country("TN", "Tunisie", "Tunisia")
  val Turkmenistan                = Country("TM", "Turkménistan", "Turkmenistan")
  val Turquie                     = Country("TR", "Turquie", "Turkey")
  val Tuvalu                      = Country("TV", "Tuvalu", "Tuvalu")
  val Ukraine                     = Country("UA", "Ukraine", "Ukraine")
  val Uruguay                     = Country("UY", "Uruguay", "Uruguay")
  val Vanuatu                     = Country("VU", "Vanuatu", "Vanuatu")
  val Vatican                     = Country("VA", "Vatican", "Vatican City")
  val Venezuela                   = Country("VE", "Vénézuéla", "Venezuela")
  val Vietnam                     = Country("VN", "Vietnam", "Vietnam")
  val Yemen                       = Country("YE", "Yémen", "Yemen")
  val Zambie                      = Country("ZM", "Zambie", "Zambia")
  val Zimbabwe                    = Country("ZW", "Zimbabwé", "Zimbabwe")
  val IlesFeroe                   = Country("FO", "Îles Féroé", "Faroe Islands")
  val Svalbard                    = Country("SJ", "Svalbard et Île Jan Mayen", "Svalbard and Jan Mayen")
  val IleBouvet                   = Country("BV", "Île Bouvet", "Bouvet Island")
  val Jersey                      = Country("JE", "Jersey", "Jersey")
  val IleMan                      = Country("IM", "Île Man", "Isle of Man")
  val Guernsey                    = Country("GG", "Guernsey", "Bailiwick of Guernsey")
  val Gibraltar                   = Country("GI", "Gibraltar", "Gibraltar")
  val Aruba                       = Country("AW", "Aruba", "Aruba")
  val HongKong                    = Country("HK", "Hong-Kong", "Hong Kong")
  val Macao                       = Country("MO", "Macao", "Macau")
  val Taiwan                      = Country("TW", "Taiwan", "Taiwan")
  val Palestine                   = Country("PS", "État de Palestine", "State of Palestine")
  val SainteHelene =
    Country("SH", "Sainte-Hélène, Ascension et Tristan da Cunha", "Saint Helena, Ascension and Tristan da Cunha")
  val TerritoireBritannique =
    Country("IO", "Territoire britannique de l'océan Indien", "British Indian Ocean Territory")
  val SaharaOccidental        = Country("EH", "Sahara occidental", "Western Sahara")
  val IlesViergesBritanniques = Country("VG", "Îles Vierges britanniques", "British Virgin Islands")
  val IlesTurques             = Country("TC", "Îles Turques-et-Caïques", "Turks and Caicos Islands")
  val Montserrat              = Country("MS", "Montserrat", "Montserrat")
  val IlesCaimans             = Country("KY", "Îles Caïmans", "Cayman Islands")
  val Bermudes                = Country("BM", "Bermudes", "Bermuda")
  val Anguilla                = Country("AI", "Anguilla", "Anguilla")
  val GeorgieDuSud =
    Country("GS", "Géorgie du Sud-et-les îles Sandwich du Sud", "South Georgia and the South Sandwich Islands")
  val Falkland              = Country("FK", "Îles Malouines ou Falkland", "Falkland Islands")
  val Groenland             = Country("GL", "Groenland", "Greenland")
  val AntillesNeerlandaises = Country("AN", "Antilles néerlandaises", "Netherlands Antilles")
  val IlesVierges           = Country("VI", "Îles Vierges des États-Unis", "United States Virgin Islands")
  val PortoRico             = Country("PR", "Porto Rico", "Puerto Rico")
  val Bonaire               = Country("BQ", "Bonaire, Saint Eustache et Saba", "Bonaire, Sint Eustatius and Saba")
  val Curacao               = Country("CW", "Curaçao", "Curaçao")
  val SaintMartinNL         = Country("SX", "Saint-Martin (royaume des Pays-Bas)", "Sint Maarten (Dutch part)")
  val Norfolk               = Country("NF", "Île Norfolk", "Norfolk Island")
  val MacDo                 = Country("HM", "Îles Heard-et-MacDonald", "Heard Island and McDonald Islands")
  val Christmas             = Country("CX", "Île Christmas", "Christmas Island")
  val IlesCocos             = Country("CC", "Îles Cocos", "Cocos (Keeling) Islands")
  val Tokelau               = Country("TK", "Tokelau", "Tokelau")
  val Pitcairn              = Country("PN", "Îles Pitcairn", "Pitcairn Islands")
  val IlesMariannes         = Country("MP", "Îles Mariannes du Nord", "Northern Mariana Islands")
  val Guam                  = Country("GU", "Guam", "Guam")
  val SamoaAmericaines      = Country("AS", "Samoa américaines", "American Samoa")

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

  private val countriesMap: Map[String, Country] = countries.map(country => country.code -> country).toMap

  def fromCode(code: String) = countriesMap(code)

  implicit val reads: Reads[Country]    = Reads.StringReads.map(fromCode)
  implicit val writes: OWrites[Country] = Json.writes[Country]

  implicit val CountryColumnType: JdbcType[Country] with BaseTypedType[Country] =
    MappedColumnType.base[Country, String](_.code, Country.fromCode)

  implicit val countryListColumnType: JdbcType[List[Country]] with BaseTypedType[List[Country]] =
    MappedColumnType.base[List[Country], List[String]](
      _.map(_.code),
      _.map(Country.fromCode)
    )
}
