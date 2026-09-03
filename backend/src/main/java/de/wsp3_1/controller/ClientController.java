package de.wsp3_1.controller;

import de.wsp3_1.model.Client;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/clients")
@CrossOrigin(origins = "http://localhost:5173")
public class ClientController {

    // our "database" — just a list living in memory
    private final List<Client> clients = new ArrayList<>();
    private final AtomicLong idCounter = new AtomicLong();

    // seed with a couple of clients so the list isn't empty on first run
    public ClientController() {
        clients.add(new Client(idCounter.incrementAndGet(), "John Doe", "john@example.com"));
        clients.add(new Client(idCounter.incrementAndGet(), "Jane Smith", "jane@example.com"));
    }

    @GetMapping
    public List<Client> getClients() {
        return clients;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Client> getClient(@PathVariable Long id) {
        return clients.stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Client> createClient(@RequestBody Client client) throws URISyntaxException {
        client.setId(idCounter.incrementAndGet());
        clients.add(client);
        return ResponseEntity.created(new URI("/api/clients/" + client.getId())).body(client);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Client> updateClient(@PathVariable Long id, @RequestBody Client updated) {
        for (Client c : clients) {
            if (c.getId().equals(id)) {
                c.setName(updated.getName());
                c.setEmail(updated.getEmail());
                return ResponseEntity.ok(c);
            }
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClient(@PathVariable Long id) {
        clients.removeIf(c -> c.getId().equals(id));
        return ResponseEntity.ok().build();
    }
}