package de.wsp3_1.auth.domain;

/**
 * Ein frisch angelegtes Lehrerkonto mit seinem Passwort im Klartext.
 *
 * <p><b>Der einzige Ort, an dem ein Passwort den Server im Klartext verlaesst.</b> Der Record entsteht
 * nur in {@link UserService#createTeacherAccounts()} und geht genau einmal in die Antwort dieses Aufrufs;
 * gespeichert wird ausschliesslich der BCrypt-Hash. Er wird nirgends protokolliert.</p>
 */
public record TeacherAccount(String username, String teacherName, String teacherShort, String password) {
}
