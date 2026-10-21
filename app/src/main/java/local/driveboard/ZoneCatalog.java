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
}
