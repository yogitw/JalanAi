package com.example.data.model

data class SeiRokanAfdeling(
    val code: String,
    val displayName: String,
    val blocks: List<String>
)

object SeiRokanCatalog {
    const val ESTATE_ID = "kebun_sei_rokan"
    const val ESTATE_NAME = "Kebun Sei Rokan"
    const val ESTATE_REGION = "Kebun Sei Rokan, Rokan Hulu, Riau"

    val afdelings: List<SeiRokanAfdeling> = listOf(
        SeiRokanAfdeling(
            code = "AFD_I",
            displayName = "Afdeling I",
            blocks = listOf(
                "I4", "I5", "J3", "J4", "J5", "K1", "K2", "K3A", "K3B", "K4", "K5",
                "L1", "L2", "L3A", "L3B", "L4", "L5", "M1", "M2", "M3A", "M3B", "M4", "M5",
                "N1", "N2", "N3A", "N3B", "N4", "N5", "O1", "O2", "O3A", "O3B", "O4", "O5",
                "P1", "P2", "P3A", "P3B", "P4B", "P4C", "P5A", "P5B",
                "Q1", "Q2", "Q3", "Q4", "Q5A", "Q5B",
                "R1", "R2", "R3", "R4", "R5A",
                "S1", "S2", "S3", "S4", "S5",
                "T1", "T2", "T3", "T4A", "T5A"
            )
        ),
        SeiRokanAfdeling(
            code = "AFD_II",
            displayName = "Afdeling II",
            blocks = listOf(
                "N6", "N7", "N8", "N9", "O10", "O11", "O6", "O7A", "O8", "O9",
                "P10", "P11", "P6", "P7", "P8", "P9",
                "Q10", "Q6A", "Q7A", "Q8", "Q9",
                "T1A", "T2A", "T3A", "T4", "T5",
                "U1", "U2", "U3", "U4", "U5", "U5A", "U6",
                "V1", "V2", "V3", "V5", "V6",
                "W2", "W3A", "W3B", "W5", "W6",
                "X2", "X3", "Y2", "Y3", "Z3"
            )
        ),
        SeiRokanAfdeling(
            code = "AFD_III",
            displayName = "Afdeling III",
            blocks = listOf(
                "O12", "O13", "O14", "O15", "O16", "O17",
                "P12", "P13", "P14", "P15", "P16",
                "Q11", "Q12", "Q13", "Q14", "Q15", "Q16B",
                "R10", "R11", "R12", "R13", "R14", "R15", "R16B",
                "S10", "S11", "S12", "S14", "S15", "S16",
                "T10", "T11", "T12", "T14", "T15", "T16",
                "U13", "U14", "U15", "U16"
            )
        ),
        SeiRokanAfdeling(
            code = "AFD_IV",
            displayName = "Afdeling IV",
            blocks = listOf(
                "GG5", "GG6", "HH4", "HH5", "HH6",
                "II4", "II5", "II6", "II7",
                "JJ4", "JJ5", "JJ6", "JJ7",
                "KK4", "KK5", "KK6", "KK7",
                "LL4", "LL5", "LL6", "LL7",
                "MM5", "MM6", "MM7",
                "NN5", "NN6", "NN7",
                "R7", "R8", "R9",
                "S7", "S8", "S9",
                "T7", "T8", "T9",
                "U10", "U11", "U12",
                "V12", "W12", "X12"
            )
        ),
        SeiRokanAfdeling(
            code = "AFD_V",
            displayName = "Afdeling V",
            blocks = listOf(
                "AA10", "AA11", "AA12", "AA13", "AA14", "AA9",
                "BB10", "BB11", "BB12", "BB13",
                "CC10", "CC11", "CC12",
                "DD11", "DD12",
                "V13", "V14", "V15", "V16",
                "W13", "W14", "W15", "W16",
                "X13", "X14", "X15", "X16",
                "Y10", "Y11", "Y12", "Y13", "Y14", "Y15", "Y16", "Y8A", "Y9",
                "Z10", "Z11", "Z12", "Z13", "Z14", "Z15", "Z9"
            )
        ),
        SeiRokanAfdeling(
            code = "AFD_VI",
            displayName = "Afdeling VI",
            blocks = listOf(
                "P17", "P18A", "P18B", "P19", "P20", "P21", "P22", "P23", "P24",
                "Q17", "Q18", "Q19", "Q20", "Q21", "Q22", "Q23", "Q24",
                "R17", "R18", "R19", "R20", "R21B", "R22", "R23", "R24",
                "S17", "S18", "S19", "S20", "S21", "S22",
                "T17", "T18", "T19", "T20", "T21",
                "U17", "U18", "U19", "U20",
                "V17B", "V18", "V19",
                "W17", "W18", "X17"
            )
        ),
        SeiRokanAfdeling(
            code = "AFD_VII",
            displayName = "Afdeling VII",
            blocks = listOf(
                "H13", "H14", "H15", "H16",
                "I13", "I14", "I15", "I16",
                "J14", "J15", "J16",
                "K14", "K15", "K16B", "K5A", "K6",
                "L14", "L15", "L16", "L6",
                "M14", "M15", "M16", "M5A", "M6",
                "N14", "N15", "N16", "N5A", "N6A",
                "O5B", "O6B", "O7",
                "P5A", "P5B", "P6A", "P6B", "P7A", "P7B",
                "Q5", "Q6", "Q7",
                "R5", "R6", "R7A",
                "S5A", "S5B", "S6A", "S6B", "S7A", "S7B",
                "T5B", "T6", "T7A",
                "U6A", "U7",
                "V6A", "V7",
                "W6A", "W7"
            )
        ),
        SeiRokanAfdeling(
            code = "AFD_VIII",
            displayName = "Afdeling VIII",
            blocks = listOf(
                "A10", "A11",
                "B10", "B11", "B12", "B13", "B14", "B15",
                "C10", "C11", "C12", "C13", "C14", "C15",
                "D10", "D11", "D12", "D13", "D14", "D15",
                "E10", "E11", "E12", "E13", "E14", "E15",
                "F10", "F11", "F12", "F13", "F14", "F15",
                "G12", "G13", "G14", "G15A", "G15B",
                "J5A", "J6", "J6A"
            )
        )
    )

    val roadTypePresets: List<String> = listOf(
        "Jalan Angkut TBS",
        "Jalan Koleksi",
        "Jalan Poros Utama",
        "Akses Jembatan / Gorong"
    )

    fun formatSegmentLabel(afdelingName: String, blockCode: String?): String {
        return if (!blockCode.isNullOrBlank()) {
            "$ESTATE_NAME • $afdelingName • Blok $blockCode"
        } else {
            "$ESTATE_NAME • $afdelingName"
        }
    }
}
