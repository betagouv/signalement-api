package models.investigation

import enumeratum.EnumEntry
import enumeratum.PlayEnum
import play.api.libs.json.Json

case class DepartmentDivisionOptionValue(code: String, name: String)

object DepartmentDivisionOptionValue {
  implicit val Writes = Json.writes[DepartmentDivisionOptionValue]
}

sealed abstract class DepartmentDivision(val code: String, val name: String) extends EnumEntry

object DepartmentDivision extends PlayEnum[DepartmentDivision] {

  val values = findValues

  case object DD01 extends DepartmentDivision("1", "DDPP DE L'AIN")
  case object DD02 extends DepartmentDivision("2", "DDPP DE L'AISNE")
  case object DD03 extends DepartmentDivision("3", "DDETSPP DE L'ALLIER")
  case object DD04 extends DepartmentDivision("4", "DDETSPP DES ALPES DE HTE-PROVENCE")
  case object DD05 extends DepartmentDivision("5", "DDETSPP DES HAUTES-ALPES")
  case object DD06 extends DepartmentDivision("6", "DDPP DES ALPES-MARITIMES")
  case object DD07 extends DepartmentDivision("7", "DDETSPP DE L'ARDECHE")
  case object DD08 extends DepartmentDivision("8", "DDETSPP DES ARDENNES")
  case object DD09 extends DepartmentDivision("9", "DDETSPP DE L'ARIEGE")
  case object DD10 extends DepartmentDivision("10", "DDETSPP DE L'AUBE")
  case object DD11 extends DepartmentDivision("11", "DDETSPP DE L'AUDE")
  case object DD12 extends DepartmentDivision("12", "DDETSPP DE L'AVEYRON")
  case object DD13 extends DepartmentDivision("13", "DDPP DES BOUCHES DU RHONE")
  case object DR_PROVENCE_ALPES_COTE_D_AZUR
      extends DepartmentDivision("13", "DREETS DE PROVENCE ALPES COTE D'AZUR - Pôle C")
  case object DD14 extends DepartmentDivision("14", "DDPP DU CALVADOS")
  case object DD15 extends DepartmentDivision("15", "DDETSPP DU CANTAL")
  case object DD16 extends DepartmentDivision("16", "DDETSPP DE LA CHARENTE")
  case object DD17 extends DepartmentDivision("17", "DDPP DE LA CHARENTE MARITIME")
  case object DD18 extends DepartmentDivision("18", "DDETSPP DU CHER")
  case object DD19 extends DepartmentDivision("19", "DDETSPP DE LA CORREZE")
  case object DD21 extends DepartmentDivision("21", "DDPP DE LA COTE D'OR")
  case object DD22 extends DepartmentDivision("22", "DDPP DES COTES-D'ARMOR")
  case object DD23 extends DepartmentDivision("23", "DDETSPP DE LA CREUSE")
  case object DD24 extends DepartmentDivision("24", "DDETSPP DE LA DORDOGNE")
  case object DD25 extends DepartmentDivision("25", "DDETSPP DU DOUBS")
  case object DR_BOURGOGNE_FRANCHE_COMTE extends DepartmentDivision("25", "DREETS BOURGOGNE-FRANCHE-COMTE")
  case object DD26 extends DepartmentDivision("26", "DDPP DE LA DROME")
  case object DD27 extends DepartmentDivision("27", "DDPP DE L'EURE")
  case object DD28 extends DepartmentDivision("28", "DDETSPP DE L'EURE ET LOIR")
  case object DD29 extends DepartmentDivision("29", "DDPP DU FINISTERE")
  case object DD30 extends DepartmentDivision("30", "DDPP DU GARD")
  case object DD31 extends DepartmentDivision("31", "DDPP DE HAUTE GARONNE")
  case object DR_OCCITANIE extends DepartmentDivision("31", "DREETS OCCITANIE")
  case object DD32 extends DepartmentDivision("32", "DDETSPP DU GERS")
  case object DD33 extends DepartmentDivision("33", "DDPP DE LA GIRONDE")
  case object DR_NOUVELLE_AQUITAINE extends DepartmentDivision("33", "DREETS NOUVELLE-AQUITAINE")
  case object DD34 extends DepartmentDivision("34", "DDPP DE L'HERAULT")
  case object ENCCRF extends DepartmentDivision("34", "ENCCRF")
  case object REPONSE_CONSO extends DepartmentDivision("34", "Service Réponse Conso")
  case object DD35 extends DepartmentDivision("35", "DDPP DE L'ILLE ET VILAINE")
  case object DR_BRETAGNE extends DepartmentDivision("35", "DREETS DE BRETAGNE")
  case object DD36 extends DepartmentDivision("36", "DDETSPP DE L'INDRE")
  case object DD37 extends DepartmentDivision("37", "DDPP D'INDRE-ET-LOIRE")
  case object DD38 extends DepartmentDivision("38", "DDPP DE L'ISERE")
  case object DD39 extends DepartmentDivision("39", "DDETSPP DU JURA")
  case object DD40 extends DepartmentDivision("40", "DDETSPP DES LANDES")
  case object DD41 extends DepartmentDivision("41", "DDETSPP DU LOIR ET CHER")
  case object DD42 extends DepartmentDivision("42", "DDPP DE LA LOIRE")
  case object DD43 extends DepartmentDivision("43", "DDETSPP DE LA HAUTE-LOIRE")
  case object DD44 extends DepartmentDivision("44", "DDPP DE LOIRE-ATLANTIQUE")
  case object DR_PAYS_DE_LA_LOIRE extends DepartmentDivision("44", "DREETS DES PAYS DE LA LOIRE")
  case object DD45 extends DepartmentDivision("45", "DDPP DU LOIRET")
  case object DR_CENTRE_VAL_DE_LOIRE extends DepartmentDivision("45", "DREETS CENTRE-VAL-DE-LOIRE")
  case object DD46 extends DepartmentDivision("46", "DDETSPP DU LOT")
  case object DD47 extends DepartmentDivision("47", "DDETSPP DU LOT-ET-GARONNE")
  case object DD48 extends DepartmentDivision("48", "DDETSPP DE LA LOZERE")
  case object DD49 extends DepartmentDivision("49", "DDPP DU MAINE-ET-LOIRE")
  case object DD50 extends DepartmentDivision("50", "DDPP DE LA MANCHE")
  case object DD51 extends DepartmentDivision("51", "DDETSPP DE LA MARNE")
  case object DD52 extends DepartmentDivision("52", "DDETSPP DE LA HAUTE-MARNE")
  case object DD53 extends DepartmentDivision("53", "DDETSPP DE LA MAYENNE")
  case object DD54 extends DepartmentDivision("54", "DDPP DE MEURTHE-ET-MOSELLE")
  case object DD55 extends DepartmentDivision("55", "DDETSPP DE LA MEUSE")
  case object DD56 extends DepartmentDivision("56", "DDPP DU MORBIHAN")
  case object DD57 extends DepartmentDivision("57", "DDPP DE MOSELLE")
  case object DD58 extends DepartmentDivision("58", "DDETSPP DE LA NIEVRE")
  case object DD59 extends DepartmentDivision("59", "DDPP DU NORD")
  case object DR_HAUTS_DE_FRANCE extends DepartmentDivision("59", "DREETS HAUTS-DE-FRANCE")
  case object DD60 extends DepartmentDivision("60", "DDPP DE L'OISE")
  case object DD61 extends DepartmentDivision("61", "DDETSPP DE L'ORNE")
  case object DD62 extends DepartmentDivision("62", "DDPP DU PAS-DE-CALAIS")
  case object DD63 extends DepartmentDivision("63", "DDPP DU PUY-DE-DOME")
  case object DD64 extends DepartmentDivision("64", "DDPP DES PYRENEES ATLANTIQUES")
  case object DD65 extends DepartmentDivision("65", "DDETSPP DES HAUTES-PYRENEES")
  case object DD66 extends DepartmentDivision("66", "DDPP DES PYRENEES-ORIENTALES")
  case object DD67 extends DepartmentDivision("67", "DDPP DU BAS-RHIN")
  case object DR_GRAND_EST extends DepartmentDivision("67", "DREETS GRAND EST")
  case object DD68 extends DepartmentDivision("68", "DDETSPP DU HAUT-RHIN")
  case object DD69 extends DepartmentDivision("69", "DDPP DU RHONE")
  case object DR_AUVERGNE_RHONE_ALPES extends DepartmentDivision("69", "DREETS AUVERGNE-RHONE-ALPES")
  case object DD70 extends DepartmentDivision("70", "DDETSPP DE HAUTE-SAONE")
  case object DD71 extends DepartmentDivision("71", "DDPP DE SAONE-ET-LOIRE")
  case object DD72 extends DepartmentDivision("72", "DDPP DE SARTHE")
  case object DD73 extends DepartmentDivision("73", "DDETSPP DE SAVOIE")
  case object DD74 extends DepartmentDivision("74", "DDPP DE HAUTE-SAVOIE")
  case object AC extends DepartmentDivision("75", "Services centraux")
  case object DD75 extends DepartmentDivision("75", "DDPP DE PARIS")
  case object DR_D_ILE_DE_FRANCE extends DepartmentDivision("75", "DRIEETS D'ILE DE FRANCE")
  case object SNE extends DepartmentDivision("75", "SERVICE NATIONAL DES ENQUETES")
  case object DD76 extends DepartmentDivision("76", "DDPP DE SEINE-MARITIME")
  case object DR_NORMANDIE extends DepartmentDivision("76", "DREETS NORMANDIE")
  case object DD77 extends DepartmentDivision("77", "DDPP DE SEINE-ET-MARNE")
  case object DD78 extends DepartmentDivision("78", "DDPP DES YVELINES")
  case object DD79 extends DepartmentDivision("79", "DDETSPP DES DEUX-SEVRES")
  case object DD80 extends DepartmentDivision("80", "DDPP DE SOMME")
  case object DD81 extends DepartmentDivision("81", "DDETSPP DU TARN")
  case object DD82 extends DepartmentDivision("82", "DDETSPP DU TARN-ET-GARONNE")
  case object DD83 extends DepartmentDivision("83", "DDPP DU VAR")
  case object DD84 extends DepartmentDivision("84", "DDPP DU VAUCLUSE")
  case object DD85 extends DepartmentDivision("85", "DDPP DE VENDEE")
  case object DD86 extends DepartmentDivision("86", "DDPP DE VIENNE")
  case object DD87 extends DepartmentDivision("87", "DDETSPP DE HAUTE VIENNE")
  case object DD88 extends DepartmentDivision("88", "DDETSPP DES VOSGES")
  case object DD89 extends DepartmentDivision("89", "DDETSPP DE L'YONNE")
  case object DD90 extends DepartmentDivision("90", "DDETSPP DU TERRITOIRE DE BELFORT")
  case object DD91 extends DepartmentDivision("91", "DDPP DE L'ESSONNE")
  case object DD92 extends DepartmentDivision("92", "DDPP DES HAUTS-DE-SEINE")
  case object DD93 extends DepartmentDivision("93", "DDPP DE SEINE-ST-DENIS")
  case object DD94 extends DepartmentDivision("94", "DDPP DU VAL-DE-MARNE")
  case object DD95 extends DepartmentDivision("95", "DDPP DU VAL-D'OISE")
  case object DD971 extends DepartmentDivision("971", "DEETS DE GUADELOUPE")
  case object DD972 extends DepartmentDivision("972", "DEETS DE MARTINIQUE")
  case object DD973 extends DepartmentDivision("973", "DGCOPOP DE GUYANE")
  case object DD974 extends DepartmentDivision("974", "DEETS DE LA REUNION")
  case object DD975 extends DepartmentDivision("975", "DCSTEP DE ST-PIERRE ET MIQUELON")
  case object DD976 extends DepartmentDivision("976", "DEETS DE MAYOTTE")
  case object DD2A extends DepartmentDivision("2", "DDETSPP DE LA CORSE DU SUD")
  case object DR_CORSE extends DepartmentDivision("2A", "DREETS DE CORSE - Pôle C")
  case object DD2B extends DepartmentDivision("2", "DDETSPP DE LA HAUTE CORSE")

}