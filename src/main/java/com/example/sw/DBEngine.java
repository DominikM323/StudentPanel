package com.example.sw;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

@Service
public class DBEngine implements UserDetailsService {
    private Connection connection;

    private MessageDigest ms;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final String secret;

    public DBEngine(JdbcTemplate jdbcTemplate, DataSource dataSource, @Value("${jwt.secret}") String secret) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.secret = secret;
        try {
            this.connect();
        } catch (SQLException e) {
        }
    }

    public void connect() throws SQLException {
//        String jdbcURL = "jdbc:h2:~/test";//jdbc:h2:~/test;MODE=ORACLE
//        String username = "sa";
//        String password = "lowvel1234";
//
//        connection = DriverManager.getConnection(jdbcURL, username, password);
        try {
            ms = MessageDigest.getInstance("SHA-256");
            this.connection = dataSource.getConnection();
        } catch (NoSuchAlgorithmException e) {
        }
        System.out.println("Connected to H2 embedded database.");
    }

    public void disconnect() throws SQLException {
        System.out.println("disconnecting..");
        connection.close();
    }

    private String getSha(String login) throws SQLException {
        String result = "";
        PreparedStatement mode = connection.prepareStatement("SET MODE oracle;");
        mode.executeUpdate();
        PreparedStatement statement = connection.prepareStatement("SELECT SHA FROM LOGINY WHERE LOGIN = ?;");
        statement.setString(1, login);
        ResultSet res = statement.executeQuery();
        if (res.next()) {
            result += res.getString(1);
        } else return "";
        return result;
    }

    private String encode(String pass) {
        byte[] sha = ms.digest(pass.getBytes(StandardCharsets.UTF_8));
        String encoded = Base64.getEncoder().encodeToString(sha);
        return encoded;
    }

    public String validate(String login, String pass) {
        String val = "";
        try {
            val = getSha(login);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (encode(pass).equals(val) && val != "") {
            String[] userInfo = FindUserID(login, encode(pass));
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", userInfo[0]);
            claims.put("userType", userInfo[1]);
            return Jwts.builder()
                    .claims(claims)
                    .subject(login)
                    .issuedAt(new Date(System.currentTimeMillis()))
                    .expiration(new Date(System.currentTimeMillis() + 3600 * 1000)) // 1h
                    .signWith(Keys.hmacShaKeyFor(secret.getBytes()))
                    .compact();
        }

        return "";
    }

    private String[] FindUserID(String login, String SHA) {
        String sql = """
                    SELECT 
                        l.ID,
                        CASE 
                            WHEN u.ID IS NOT NULL THEN 'uczen'
                            WHEN le.ID IS NOT NULL THEN 'lektor'
                            ELSE 'nieznany'
                        END AS user_type
                    FROM loginy l
                    LEFT JOIN UCZNIOWIE u ON l.ID = u.id_login
                    LEFT JOIN LEKTORZY le ON l.ID = le.id_login
                    WHERE l.LOGIN=? 
                """;
        String[] userinfo = {"", "", ""};
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setString(1, login);

            try {
                ResultSet rs = ps.executeQuery();
                //System.out.println("querying:" +rs);
                if (rs.next()) {
                    int userId = rs.getInt("ID");
                    String userType = rs.getString("user_type");

                    userinfo[0] = String.valueOf(userId);
                    userinfo[1] = userType;
                    userinfo[2] = SHA;
                    //System.out.println(userinfo[0] + "|" + userinfo[1]);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return userinfo;
    }

    String getLoginById(int ID){
        String sql = "SELECT l.LOGIN FROM LOGINY l WHERE l.ID = ?";
        String result = "";
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setInt(1, ID);

            try {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                   result = rs.getString(1);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    ArrayList<String> getExistingYearNames() throws SQLException {
        PreparedStatement st = connection.prepareStatement("SELECT * FROM ROK ORDER BY rok DESC;");
        ResultSet res = st.executeQuery();
        ArrayList<String> years = new ArrayList<String>();
        while (res.next()) {
            years.add(res.getString("ROK"));
        }

        return years;
    }

    @Override
    public CustomUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String[] foundDetails = this.FindUserID(username, "");
        CustomUserDetails details = null;
        if (foundDetails[0] == null || foundDetails[0].isEmpty()) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        try{
             details = new CustomUserDetails(username, "", Long.valueOf(foundDetails[0]), foundDetails[1]);
        }catch(NumberFormatException e){
            return null;
        }

        System.out.println("detail: "+foundDetails[1]);
        return details;
    }


    public List<LekcjaDTO> findLekcjeForStudent(String rokNazwa, int uczenId, LocalDate startDate, LocalDate endDate) throws SQLException {
        String sqlRok = "SELECT ID FROM ROK WHERE ROK = ?";//find year ID
        Integer rokId = null;

        try (PreparedStatement ps = connection.prepareStatement(sqlRok)) {
            ps.setString(1, rokNazwa);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    rokId = rs.getInt("ID");
                } else {
                    throw new SQLException("Nie znaleziono roku: " + rokNazwa);
                }
            }
        }

        String sqlGrupa = "SELECT ID_G FROM U_G_R WHERE ID_U = ? AND ID_R = ?";//find group ID
        Integer grupaId = null;

        try (PreparedStatement ps = connection.prepareStatement(sqlGrupa)) {
            ps.setInt(1, uczenId);
            ps.setInt(2, rokId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    grupaId = rs.getInt("ID_G");
                } else {
                    throw new SQLException("Uczeń " + uczenId + " nie należy do żadnej grupy w roku " + rokNazwa);
                }
            }
        }

        String sql = "SELECT \n" +
                "    l.ID,\n" +
                "    l.DATA,\n" +
                "    l.GODZINA_ROZP,\n" +
                "    l.GODZINA_KON,\n" +
                "    l.PRZEDMIOT,\n" +
                "    l.LOKACJA,\n" +
                "    CONCAT(le.IMIE, ' ', le.NAZWISKO) AS LEKTOR,\n" +
                "    l.GRUPA_ID,\n" +
                "    l.ROK_ID\n" +
                "FROM LEKCJA l\n" +
                "JOIN LEKTORZY le ON l.LEKTOR_ID = le.ID\n" +
                "WHERE l.ROK_ID = ? \n" +
                "  AND l.GRUPA_ID = ? \n" +
                "  AND l.DATA BETWEEN ? AND ?\n" +
                "ORDER BY l.DATA, l.GODZINA_ROZP;";

        List<LekcjaDTO> result = new ArrayList<>();

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, rokId);
            ps.setInt(2, grupaId);
            ps.setDate(3, Date.valueOf(startDate));
            ps.setDate(4, Date.valueOf(endDate));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LekcjaDTO lekcja = new LekcjaDTO();
                    lekcja.setId(0);
                    lekcja.setId(rs.getInt("ID"));
                    lekcja.setData(rs.getDate("DATA"));
                    lekcja.setGodzinaRozp(rs.getTime("GODZINA_ROZP"));
                    lekcja.setGodzinaKon(rs.getTime("GODZINA_KON"));
                    lekcja.setPrzedmiot(rs.getString("PRZEDMIOT"));
                    lekcja.setLokacja(rs.getString("LOKACJA"));
                    lekcja.setLektor(rs.getString("LEKTOR"));
                    lekcja.setGrupaId(rs.getInt("GRUPA_ID"));
                    lekcja.setRokId(rs.getInt("ROK_ID"));
                    result.add(lekcja);
                }
            }
        }

        return result;
    }

    public List<LekcjaDTO> findLekcjeForLektor(String rokNazwa, int lektorId, LocalDate startDate, LocalDate endDate) throws SQLException {

        String sqlRok = "SELECT ID FROM ROK WHERE ROK = ?";
        Integer rokId = null;

        try (PreparedStatement ps = connection.prepareStatement(sqlRok)) {
            ps.setString(1, rokNazwa);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    rokId = rs.getInt("ID");
                } else {
                    throw new SQLException("Nie znaleziono roku: " + rokNazwa);
                }
            }
        }

        // --- 2. Zapytanie o lekcje danego lektora ---
        String sql = "SELECT \n" +
                "        l.ID,\n" +
                "        l.DATA,\n" +
                "        l.GODZINA_ROZP,\n" +
                "        l.GODZINA_KON,\n" +
                "        l.PRZEDMIOT,\n" +
                "        l.LOKACJA,\n" +
                "        CONCAT(le.IMIE, ' ', le.NAZWISKO) AS LEKTOR,\n" +
                "        l.GRUPA_ID,\n" +
                "        g.NAZWA AS GRUPA_NAZWA,\n" +
                "        l.ROK_ID\n" +
                "    FROM LEKCJA l\n" +
                "    JOIN LEKTORZY le ON l.LEKTOR_ID = le.ID\n" +
                "    JOIN GRUPY g ON l.GRUPA_ID = g.ID\n" +
                "    WHERE l.ROK_ID = ?\n" +
                "      AND l.LEKTOR_ID = ?\n" +
                "      AND l.DATA BETWEEN ? AND ?\n" +
                "    ORDER BY l.DATA, l.GODZINA_ROZP;";

        List<LekcjaDTO> result = new ArrayList<>();

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, rokId);
            ps.setInt(2, lektorId);
            ps.setDate(3, Date.valueOf(startDate));
            ps.setDate(4, Date.valueOf(endDate));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LekcjaDTO lekcja = new LekcjaDTO();
                    lekcja.setId(rs.getInt("ID"));
                    lekcja.setData(rs.getDate("DATA"));
                    lekcja.setGodzinaRozp(rs.getTime("GODZINA_ROZP"));
                    lekcja.setGodzinaKon(rs.getTime("GODZINA_KON"));
                    lekcja.setPrzedmiot(rs.getString("PRZEDMIOT"));
                    lekcja.setLokacja(rs.getString("LOKACJA"));
                    lekcja.setLektor(rs.getString("GRUPA_NAZWA"));
                    lekcja.setGrupaId(rs.getInt("GRUPA_ID"));
                    lekcja.setRokId(rs.getInt("ROK_ID"));
                    result.add(lekcja);
                }
            }
        }

        return result;
    }

    public List<LektorDTO> getAllLectors() {
        List<LektorDTO> lektorzy = new ArrayList<>();
        String sql = "SELECT ID, IMIE || ' ' || NAZWISKO as Nazwisko FROM LEKTORZY ";
        try{
            PreparedStatement ps = connection.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                int id = rs.getInt("ID");
                String nazwisko = rs.getString("NAZWISKO");
                lektorzy.add(new LektorDTO(id, nazwisko));
            }
        }catch(SQLException e){
            e.printStackTrace();
        }
        return lektorzy;
    }

    public String getLectorNameByID(int ID) {
        String sql = "SELECT IMIE || ' ' || NAZWISKO AS NAZWISKO FROM LEKTORZY WHERE ID = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("NAZWISKO");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<GrupaDTO> getAllGroups() {
        List<GrupaDTO> grupy = new ArrayList<>();
        String sql = "SELECT ID, NAZWA FROM GRUPY";

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Long id = rs.getLong("ID");
                String nazwa = rs.getString("NAZWA");
                grupy.add(new GrupaDTO(id, nazwa));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return grupy;
    }

    public LekcjaDTO getLessonById(int lessonId) {
        String sql = "SELECT ID, DATA, GODZINA_ROZP, GODZINA_KON, PRZEDMIOT, LOKACJA, LEKTOR_ID, GRUPA_ID, ROK_ID " +
                "FROM LEKCJA WHERE ID = ?";
        LekcjaDTO lesson = null;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, lessonId); // wstawiamy parametr do prepared statement
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("ID");
                    Date data = rs.getDate("DATA");
                    Time godzinaRozp = rs.getTime("GODZINA_ROZP");
                    Time godzinaKon = rs.getTime("GODZINA_KON");
                    String przedmiot = rs.getString("PRZEDMIOT");
                    String lokacja = rs.getString("LOKACJA");


                    int lektorId = rs.getInt("LEKTOR_ID");
                    String lektor = getLectorNameByID(lektorId);

                    int grupaId = rs.getInt("GRUPA_ID");
                    int rokId = rs.getInt("ROK_ID");

                    lesson = new LekcjaDTO(id, data, godzinaRozp, godzinaKon,
                            przedmiot, lokacja, lektorId, lektor,
                            grupaId, rokId);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return lesson;
    }


    public boolean updateLesson(LekcjaDTO dto) throws SQLException {
        String sql = "UPDATE LEKCJA SET " +
                "DATA = ?, " +
                "GODZINA_ROZP = ?, " +
                "GODZINA_KON = ?, " +
                "PRZEDMIOT = ?, " +
                "LOKACJA = ?, " +
                "LEKTOR_ID = ?, " +
                "GRUPA_ID = ? " +

                "WHERE ID = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setDate(1, dto.getData());
            ps.setTime(2, dto.getGodzinaRozp());
            ps.setTime(3, dto.getGodzinaKon());
            ps.setString(4, dto.getPrzedmiot());
            ps.setString(5, dto.getLokacja());
            ps.setInt(6, dto.getLektorId());
            ps.setInt(7, dto.getGrupaId());
            ps.setInt(8, dto.getId());

            int affected = ps.executeUpdate();
            return affected > 0; // czy zaktualizowano
        }
    }

    public boolean createLesson(LekcjaDTO dto,String yearName) throws SQLException {
        String sql = "INSERT INTO LEKCJA (DATA, GODZINA_ROZP, GODZINA_KON, PRZEDMIOT, LOKACJA, LEKTOR_ID, GRUPA_ID, ROK_ID) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String sqlRok = "SELECT ID FROM ROK WHERE ROK = ?";
        int rokId = 1;
        try (PreparedStatement ps = connection.prepareStatement(sqlRok)) {
            ps.setString(1, yearName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    rokId = rs.getInt("ID");
                } else {
                    throw new SQLException("Nie znaleziono roku: " + yearName);
                }
            }
        }

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(dto.getData().toLocalDate()));
            ps.setTime(2, dto.getGodzinaRozp());
            ps.setTime(3, dto.getGodzinaKon());
            ps.setString(4, dto.getPrzedmiot());
            ps.setString(5, dto.getLokacja());
            ps.setInt(6, dto.getLektorId());
            ps.setInt(7, dto.getGrupaId());
            ps.setInt(8, rokId); // lub wybierz domyślny rok, jeśli nie chcesz wymagać od frontu
            return ps.executeUpdate() == 1;
        }
    }

    public boolean deleteLesson(int id) throws SQLException {
        String sql = "DELETE FROM LEKCJA WHERE ID = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            int affected = ps.executeUpdate();
            return affected > 0;
        }
    }

    public List<LektorAccountDTO> getAllLectorData() {

        String sql = """
                    SELECT l.id, l.imie, l.nazwisko, l.adres, l.telefon,
                           l.umowa, l.umowa_od, l.umowa_do,
                           lg.id AS login_id, lg.login
                    FROM lektorzy l
                    LEFT JOIN loginy lg ON lg.id = l.id_login
                    ORDER BY l.id
                """;
        List<LektorAccountDTO> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                LektorAccountDTO dto = new LektorAccountDTO();
                dto.id = rs.getInt("id");
                dto.imie = rs.getString("imie");
                dto.nazwisko = rs.getString("nazwisko");
                dto.adres = rs.getString("adres");
                dto.telefon = rs.getString("telefon");

                //dto.umowa = rs.getString("umowa");

                Date od = rs.getDate("umowa_od");
                Date d2 = rs.getDate("umowa_do");

                dto.umowaOd = (od == null ? null : od.toLocalDate());
                dto.umowaDo = (d2 == null ? null : d2.toLocalDate());

                dto.loginId = rs.getInt("login_id");
                dto.login   = rs.getString("login");

                list.add(dto);
            }
        }catch(SQLException e){
            e.printStackTrace();
        }

        return list;
    }

    private String sha256(String input) {
        try {
            byte[] hash = ms.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Błąd hashowania SHA-256");
        }
    }

    public LektorAccountDTO createLector(NewLektorDTO dto) {

        if (dto.getLogin() == null || dto.getLogin().isBlank())
            throw new RuntimeException("Login jest wymagany");
        if (dto.getHaslo() == null || dto.getHaslo().isBlank())
            throw new RuntimeException("Hasło jest wymagane");

        try {

            connection.setAutoCommit(false);

            //czy login istnieje
            String checkLoginSql = "SELECT COUNT(*) FROM LOGINY WHERE LOGIN = ?";
            try (PreparedStatement ps = connection.prepareStatement(checkLoginSql)) {
                ps.setString(1, dto.getLogin());
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) > 0)
                    throw new RuntimeException("Login już istnieje!");
            }

            //dodaj login
            String insertLoginSql =
                    "INSERT INTO LOGINY (LOGIN, SHA) VALUES (?, ?)";

            int loginId;
            try (PreparedStatement ps = connection.prepareStatement(insertLoginSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, dto.getLogin());
                ps.setString(2, encode(dto.getHaslo()));
                ps.executeUpdate();

                ResultSet keys = ps.getGeneratedKeys();
                if (!keys.next()) throw new RuntimeException("Błąd tworzenia loginu.");
                loginId = keys.getInt(1);
            }

           //doda lekora
            String insertLectorSql =
                    "INSERT INTO LEKTORZY (IMIE, NAZWISKO, ADRES, TELEFON, UMOWA_OD, UMOWA_DO, ID_LOGIN) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)";

            int lektorId;
            try (PreparedStatement ps = connection.prepareStatement(insertLectorSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, dto.getImie());
                ps.setString(2, dto.getNazwisko());
                ps.setString(3, dto.getAdres());
                ps.setString(4, dto.getTelefon());
                ps.setDate(5, dto.getUmowaOd());
                ps.setDate(6, dto.getUmowaDo());
                ps.setInt(7, loginId);

                ps.executeUpdate();

                ResultSet keys = ps.getGeneratedKeys();
                if (!keys.next()) throw new RuntimeException("Błąd tworzenia lektora.");
                lektorId = keys.getInt(1);
            }

            connection.commit();


            LektorAccountDTO acc = new LektorAccountDTO();

            return acc;

        } catch (Exception ex) {
            try { connection.rollback(); } catch (SQLException ignore) {}
            throw new RuntimeException("Błąd podczas tworzenia lektora: " + ex.getMessage(), ex);
        } finally {
            try { connection.setAutoCommit(true); } catch (Exception ignore) {}
        }
    }

    public boolean deleteLectorById(int id) {
        String sqlFindLogin = "SELECT ID_LOGIN FROM LEKTORZY WHERE ID = ?";
        String sqlDeleteLector = "DELETE FROM LEKTORZY WHERE ID = ?";
        String sqlDeleteLogin = "DELETE FROM LOGINY WHERE ID = ?";

        try {
            connection.setAutoCommit(false);

            // sprawdz czy lektor istnieje i pobierz konto
            Integer loginId = null;

            try (PreparedStatement ps = connection.prepareStatement(sqlFindLogin)) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    loginId = rs.getInt("ID_LOGIN");
                } else {
                    connection.rollback();
                    return false; // brak lektora
                }
            }

            // del lektor
            try (PreparedStatement ps = connection.prepareStatement(sqlDeleteLector)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            // del login (jeśli istnieje)
            if (loginId != null) {
                try (PreparedStatement ps = connection.prepareStatement(sqlDeleteLogin)) {
                    ps.setInt(1, loginId);
                    ps.executeUpdate();
                }
            }

            connection.commit();
            return true;

        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException ex) {}
            e.printStackTrace();
            return false;
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ex) {}
        }
    }

    public boolean updateLector(UpdateLektorDTO dto) {
        String updateLektorSql = "UPDATE LEKTORZY SET IMIE=?, NAZWISKO=?, ADRES=?, TELEFON=?, UMOWA_OD=?, UMOWA_DO=? WHERE ID=?";
        String updateLoginSql = "UPDATE LOGINY SET LOGIN=? WHERE ID=?";

        try (PreparedStatement psLektor = connection.prepareStatement(updateLektorSql);
             PreparedStatement psLogin = connection.prepareStatement(updateLoginSql)) {

            // Aktualizacja lektora
            psLektor.setString(1, dto.getImie());
            psLektor.setString(2, dto.getNazwisko());
            psLektor.setString(3, dto.getAdres());
            psLektor.setString(4, dto.getTelefon());
            psLektor.setString(5, dto.getUmowaOd());
            psLektor.setString(6, dto.getUmowaDo());
            psLektor.setLong(7, dto.getId());
            psLektor.executeUpdate();

            // Aktualizacja loginu (bez hasla)
            psLogin.setString(1, dto.getLogin());
            psLogin.setLong(2, dto.getId());
            psLogin.executeUpdate();

            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean resetLectorPassword(long lectorId, String newPassword) {

        String sql = "UPDATE LOGINY SET SHA=? WHERE ID=?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            String encoded = encode(newPassword); //hash

            ps.setString(1, encoded);
            ps.setLong(2, lectorId);
            ps.executeUpdate();

            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }


}