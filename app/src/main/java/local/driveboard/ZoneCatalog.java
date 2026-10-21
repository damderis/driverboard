package local.driveboard;

/** Bundled zone names from https://api.waktusolat.app/zones, captured 2026-10-21. */
public final class ZoneCatalog {
    public static final class Zone {
        public final String code;
        public final String name;

        Zone(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }

    public static final class Centre {
        public final double latitude;
        public final double longitude;
        public final String place;

        Centre(double latitude, double longitude, String place) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.place = place;
        }
    }

    // Area-weighted polygon centres from the Waktu Solat project's
    // malaysia.district-jakim.geojson (mptwaktusolat/jakim.geojson, master).
    // The six special zones absent from that dataset use representative points.
    private static final String CENTRES =
        "JHR01|2.49860|104.44943\n" +
        "JHR02|1.91299|103.80845\n" +
        "JHR03|1.93838|103.39134\n" +
        "JHR04|2.23646|102.90383\n" +
        "KDH01|6.26406|100.41186\n" +
        "KDH02|5.81940|100.50908\n" +
        "KDH03|6.11257|100.78158\n" +
        "KDH04|5.70894|100.86630\n" +
        "KDH05|5.34730|100.63794\n" +
        "KDH06|6.34590|99.79122\n" +
        "KDH07|5.80|100.43\n" +
        "KTN01|5.69588|102.17899\n" +
        "KTN02|5.04000|101.94034\n" +
        "MLK01|2.32298|102.30275\n" +
        "NGS01|2.74426|102.46633\n" +
        "NGS02|2.85656|102.15075\n" +
        "NGS03|2.67451|101.90557\n" +
        "PHG01|2.78807|104.17226\n" +
        "PHG02|3.27234|103.12994\n" +
        "PHG03|3.88038|102.52152\n" +
        "PHG04|4.04468|101.91308\n" +
        "PHG05|3.30|101.77\n" +
        "PHG06|4.47306|101.47656\n" +
        "PHG07|2.80|103.50\n" +
        "PRK01|4.35404|101.16057\n" +
        "PRK02|4.59575|101.11121\n" +
        "PRK03|5.32196|101.09462\n" +
        "PRK04|5.52106|101.46279\n" +
        "PRK05|4.17217|100.85692\n" +
        "PRK06|4.95157|100.75706\n" +
        "PRK07|4.86|100.79\n" +
        "PLS01|6.50444|100.24090\n" +
        "PNG01|5.36615|100.40137\n" +
        "SBH01|5.76668|117.98152\n" +
        "SBH02|5.49977|117.40114\n" +
        "SBH03|4.98471|118.36136\n" +
        "SBH04|4.47435|117.63143\n" +
        "SBH05|6.70219|116.94470\n" +
        "SBH06|6.08|116.56\n" +
        "SBH07|5.95830|116.49215\n" +
        "SBH08|4.95825|116.37761\n" +
        "SBH09|4.93807|115.76253\n" +
        "SWK01|4.40235|115.27455\n" +
        "SWK02|3.59329|114.60263\n" +
        "SWK03|2.39638|113.82741\n" +
        "SWK04|2.24266|112.48156\n" +
        "SWK05|2.19498|111.62577\n" +
        "SWK06|1.42078|111.51128\n" +
        "SWK07|1.24059|110.68380\n" +
        "SWK08|1.52248|110.08815\n" +
        "SWK09|1.65|113.69\n" +
        "SGR01|3.26622|101.65655\n" +
        "SGR02|3.52527|101.21720\n" +
        "SGR03|2.91750|101.45249\n" +
        "TRG01|5.21014|103.12321\n" +
        "TRG02|5.51935|102.66095\n" +
        "TRG03|5.05012|102.77404\n" +
        "TRG04|4.48492|103.18040\n" +
        "WLY01|3.11087|101.68952\n" +
        "WLY02|5.30798|115.22059";

    private static final String DATA =
        "JHR01|Johor · Pulau Aur dan Pulau Pemanggil\n"+
        "JHR02|Johor · Johor Bahru, Kota Tinggi, Mersing, Kulai\n"+
        "JHR03|Johor · Kluang, Pontian\n"+
        "JHR04|Johor · Batu Pahat, Muar, Segamat, Gemas Johor, Tangkak\n"+
        "KDH01|Kedah · Kota Setar, Kubang Pasu, Pokok Sena (Daerah Kecil)\n"+
        "KDH02|Kedah · Kuala Muda, Yan, Pendang\n"+
        "KDH03|Kedah · Padang Terap, Sik\n"+
        "KDH04|Kedah · Baling\n"+
        "KDH05|Kedah · Bandar Baharu, Kulim\n"+
        "KDH06|Kedah · Langkawi\n"+
        "KDH07|Kedah · Puncak Gunung Jerai\n"+
        "KTN01|Kelantan · Bachok, Kota Bharu, Machang, Pasir Mas, Pasir Puteh, Tanah Merah, Tumpat, Kuala Krai, Mukim Chiku\n"+
        "KTN02|Kelantan · Gua Musang (Daerah Galas Dan Bertam), Jeli, Jajahan Kecil Lojing\n"+
        "MLK01|Melaka · SELURUH NEGERI MELAKA\n"+
        "NGS01|Negeri Sembilan · Tampin, Jempol\n"+
        "NGS02|Negeri Sembilan · Jelebu, Kuala Pilah, Rembau\n"+
        "NGS03|Negeri Sembilan · Port Dickson, Seremban\n"+
        "PHG01|Pahang · Pulau Tioman\n"+
        "PHG02|Pahang · Kuantan, Pekan, Muadzam Shah\n"+
        "PHG03|Pahang · Jerantut, Temerloh, Maran, Bera, Chenor, Jengka\n"+
        "PHG04|Pahang · Bentong, Lipis, Raub\n"+
        "PHG05|Pahang · Genting Sempah, Janda Baik, Bukit Tinggi\n"+
        "PHG06|Pahang · Cameron Highlands, Genting Higlands, Bukit Fraser\n"+
        "PHG07|Pahang · Zon Khas Daerah Rompin, (Mukim Rompin, Mukim Endau, Mukim Pontian)\n"+
        "PRK01|Perak · Tapah, Slim River, Tanjung Malim\n"+
        "PRK02|Perak · Kuala Kangsar, Sg. Siput , Ipoh, Batu Gajah, Kampar\n"+
        "PRK03|Perak · Lenggong, Pengkalan Hulu, Grik\n"+
        "PRK04|Perak · Temengor, Belum\n"+
        "PRK05|Perak · Kg Gajah, Teluk Intan, Bagan Datuk, Seri Iskandar, Beruas, Parit, Lumut, Sitiawan, Pulau Pangkor\n"+
        "PRK06|Perak · Selama, Taiping, Bagan Serai, Parit Buntar\n"+
        "PRK07|Perak · Bukit Larut\n"+
        "PLS01|Perlis · SELURUH NEGERI PERLIS\n"+
        "PNG01|Pulau Pinang · SELURUH NEGERI PULAU PINANG\n"+
        "SBH01|Sabah · Bahagian Sandakan (Timur), Bukit Garam, Semawang, Temanggong, Tambisan, Bandar Sandakan, Sukau\n"+
        "SBH02|Sabah · Beluran, Telupid, Pinangah, Terusan, Kuamut, Bahagian Sandakan (Barat)\n"+
        "SBH03|Sabah · Lahad Datu, Silabukan, Kunak, Sahabat, Semporna, Tungku, Bahagian Tawau (Timur)\n"+
        "SBH04|Sabah · Bandar Tawau, Balong, Merotai, Kalabakan, Bahagian Tawau (Barat)\n"+
        "SBH05|Sabah · Kudat, Kota Marudu, Pitas, Pulau Banggi, Bahagian Kudat\n"+
        "SBH06|Sabah · Gunung Kinabalu\n"+
        "SBH07|Sabah · Kota Kinabalu, Ranau, Kota Belud, Tuaran, Penampang, Papar, Putatan, Bahagian Pantai Barat\n"+
        "SBH08|Sabah · Pensiangan, Keningau, Tambunan, Nabawan, Bahagian Pendalaman (Atas)\n"+
        "SBH09|Sabah · Beaufort, Kuala Penyu, Sipitang, Tenom, Long Pasia, Membakut, Weston, Bahagian Pendalaman (Bawah)\n"+
        "SWK01|Sarawak · Limbang, Lawas, Sundar, Trusan\n"+
        "SWK02|Sarawak · Miri, Niah, Bekenu, Sibuti, Marudi\n"+
        "SWK03|Sarawak · Pandan, Belaga, Suai, Tatau, Sebauh, Bintulu\n"+
        "SWK04|Sarawak · Sibu, Mukah, Dalat, Song, Igan, Oya, Balingian, Kanowit, Kapit\n"+
        "SWK05|Sarawak · Sarikei, Matu, Julau, Rajang, Daro, Bintangor, Belawai\n"+
        "SWK06|Sarawak · Lubok Antu, Sri Aman, Roban, Debak, Kabong, Lingga, Engkelili, Betong, Spaoh, Pusa, Saratok\n"+
        "SWK07|Sarawak · Serian, Simunjan, Samarahan, Sebuyau, Meludam\n"+
        "SWK08|Sarawak · Kuching, Bau, Lundu, Sematan\n"+
        "SWK09|Sarawak · Zon Khas (Kampung Patarikan)\n"+
        "SGR01|Selangor · Gombak, Petaling, Sepang, Hulu Langat, Hulu Selangor, Shah Alam\n"+
        "SGR02|Selangor · Kuala Selangor, Sabak Bernam\n"+
        "SGR03|Selangor · Klang, Kuala Langat\n"+
        "TRG01|Terengganu · Kuala Terengganu, Marang, Kuala Nerus\n"+
        "TRG02|Terengganu · Besut, Setiu\n"+
        "TRG03|Terengganu · Hulu Terengganu\n"+
        "TRG04|Terengganu · Dungun, Kemaman\n"+
        "WLY01|Kuala Lumpur, Putrajaya\n"+
        "WLY02|Wilayah Persekutuan · Labuan";
    private static final Zone[] OPTIONS = parse();

    private ZoneCatalog() {}

    private static Zone[] parse() {
        String[] lines = DATA.split("\\n");
        Zone[] zones = new Zone[lines.length];
        for (int i = 0; i < lines.length; i++) {
            int at = lines[i].indexOf('|');
            zones[i] = new Zone(lines[i].substring(0, at), lines[i].substring(at + 1));
        }
        return zones;
    }

    public static Zone[] options() {
        return OPTIONS.clone();
    }

    public static String name(String code) {
        for (Zone z : OPTIONS) {
            if (z.code.equals(code)) {
                return z.name;
            }
        }
        return code;
    }

    public static Centre centre(String code) {
        for (String line : CENTRES.split("\\n")) {
            String[] fields = line.split("\\|");
            if (fields[0].equals(code)) {
                return new Centre(Double.parseDouble(fields[1]),
                        Double.parseDouble(fields[2]), name(code));
            }
        }
        return centre("WLY01");
    }
}
