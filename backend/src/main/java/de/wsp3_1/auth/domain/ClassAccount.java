package de.wsp3_1.auth.domain;

/**
 * Ein frisch angelegtes Klassenkonto (E-50) mit seinem Passwort im Klartext.
 *
 * <p><b>Der einzige Ort, an dem ein Passwort den Server im Klartext verlaesst.</b> Der Record entsteht
 * nur in {@link UserService#createClassAccounts()} und geht genau einmal in die Antwort dieses Aufrufs;
 * gespeichert wird ausschliesslich der BCrypt-Hash. Er wird nirgends protokolliert.</p>
 */
public record ClassAccount(String username, String classShort, String password) {
}
