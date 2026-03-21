package com.medoq.backend.service.ussd;

import com.fasterxml.jackson.core.type.TypeReference;
import com.medoq.backend.dto.reservation.CreateReservationRequest;
import com.medoq.backend.dto.reservation.ReservationItemRequest;
import com.medoq.backend.entity.Reservation;
import com.medoq.backend.entity.User;
import com.medoq.backend.repository.ReservationRepository;
import com.medoq.backend.repository.UserRepository;
import com.medoq.backend.service.PaymentService;
import com.medoq.backend.service.ReservationService;
import com.medoq.backend.service.MedicationSearchService;
import com.medoq.backend.dto.search.MedicationSearchResultDto;
import com.medoq.backend.dto.search.PharmacyStockDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Drives the full USSD menu tree for *Medoq#.
 *
 * Response format (Africa's Talking):
 *   "CON <text>"  — keep session open (user can input more)
 *   "END <text>"  — terminate session
 *
 * The {@code text} field contains all accumulated inputs joined by "*".
 * We split on "*" and navigate the tree based on depth + values.
 *
 * Tree structure:
 *   ""                               → Main menu (3 options)
 *   "1"                              → Prompt: medication name
 *   "1*{query}"                      → List pharmacies with stock
 *   "1*{query}*{idx}"                → Confirm reservation
 *   "1*{query}*{idx}*1"              → Create reservation + prompt payment
 *   "1*{query}*{idx}*1*1"            → Initiate Orange Money payment
 *   "1*{query}*{idx}*1*2"            → Skip payment, END
 *   "2"                              → List my reservations
 *   "2*{idx}"                        → Reservation detail, END
 *   "3"                              → Prompt: reservation reference
 *   "3*{ref}"                        → Cancel confirmation
 *   "3*{ref}*1"                      → Confirm cancel, END
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UssdFlowService {

    private static final int MAX_RESULTS    = 5;  // AT screen is small
    private static final int MAX_RESERVATIONS = 5;

    private final UssdSessionService       session;
    private final MedicationSearchService  searchService;
    private final ReservationService       reservationService;
    private final ReservationRepository    reservationRepository;
    private final UserRepository           userRepository;
    private final PaymentService           paymentService;

    // ── Entry point ──────────────────────────────────────────────────────────

    public String handle(String sessionId, String phone, String rawText) {
        String text   = rawText == null ? "" : rawText.trim();
        String[] parts = text.isEmpty() ? new String[0] : text.split("\\*", -1);
        int depth = parts.length;

        log.debug("USSD [{}] phone={} text='{}' depth={}", sessionId, phone, text, depth);

        try {
            return switch (depth) {
                case 0 -> mainMenu();
                case 1 -> handleDepth1(sessionId, phone, parts[0]);
                case 2 -> handleDepth2(sessionId, phone, parts[0], parts[1]);
                case 3 -> handleDepth3(sessionId, phone, parts[0], parts[1], parts[2]);
                case 4 -> handleDepth4(sessionId, phone, parts[0], parts[1], parts[2], parts[3]);
                case 5 -> handleDepth5(sessionId, phone, parts[0], parts[1], parts[2], parts[3], parts[4]);
                default -> end("Option invalide. Composez *338# pour recommencer.");
            };
        } catch (Exception e) {
            log.error("USSD flow error for session {}: {}", sessionId, e.getMessage(), e);
            return end("Une erreur est survenue. Réessayez en composant *338#.");
        }
    }

    // ── Depth 0 : main menu ──────────────────────────────────────────────────

    private String mainMenu() {
        return con("""
                Bienvenue sur Medoq 💊
                1. Rechercher médicament
                2. Mes réservations
                3. Annuler réservation
                0. Quitter""");
    }

    // ── Depth 1 ───────────────────────────────────────────────────────────────

    private String handleDepth1(String sid, String phone, String choice) {
        return switch (choice) {
            case "1"  -> con("Entrez le nom du médicament\n(ex: Paracétamol, Amoxicilline):");
            case "2"  -> myReservationsList(sid, phone);
            case "3"  -> con("Entrez votre numéro de réservation\n(ex: MQ-240321-01000):");
            case "0"  -> end("Merci d'utiliser Medoq. À bientôt !");
            default   -> con("Option invalide.\n" + mainMenu().substring(4));
        };
    }

    // ── Depth 2 ───────────────────────────────────────────────────────────────

    private String handleDepth2(String sid, String phone, String menu, String input) {
        return switch (menu) {
            case "1" -> searchResults(sid, input);
            case "2" -> myReservationDetail(sid, input);
            case "3" -> cancelConfirm(phone, input);
            default  -> end("Session expirée. Recomposez *338#.");
        };
    }

    // ── Depth 3 ───────────────────────────────────────────────────────────────

    private String handleDepth3(String sid, String phone, String menu, String query, String choice) {
        return switch (menu) {
            case "1" -> {
                if ("0".equals(choice)) yield mainMenu();
                yield pharmacyConfirm(sid, query, choice);
            }
            case "3" -> {
                // "3*{ref}*1" — confirm cancel
                if ("1".equals(choice)) yield doCancelReservation(phone, query);
                yield end("Annulation annulée. À bientôt !");
            }
            default -> end("Session expirée. Recomposez *338#.");
        };
    }

    // ── Depth 4 ───────────────────────────────────────────────────────────────

    private String handleDepth4(String sid, String phone,
                                  String menu, String query, String idx, String choice) {
        if (!"1".equals(menu)) return end("Session expirée. Recomposez *338#.");
        return switch (choice) {
            case "1" -> doCreateReservation(sid, phone, query, idx);
            case "2" -> end("Réservation annulée. À bientôt !");
            default  -> pharmacyConfirm(sid, query, idx); // Re-show confirmation
        };
    }

    // ── Depth 5 ───────────────────────────────────────────────────────────────

    private String handleDepth5(String sid, String phone,
                                  String menu, String query, String idx,
                                  String reservationIdStr, String choice) {
        if (!"1".equals(menu)) return end("Session expirée. Recomposez *338#.");

        String reservationId = session.load(sid, "created_reservation_id", String.class);
        if (reservationId == null) return end("Session expirée. Recomposez *338#.");

        return switch (choice) {
            case "1" -> doPayOrangeMoney(sid, phone, reservationId);
            case "2" -> {
                session.clear(sid);
                Reservation r = reservationRepository.findById(UUID.fromString(reservationId)).orElse(null);
                String ref = r != null ? r.getReference() : reservationId;
                yield end("✅ Réservation confirmée !\nRéf: " + ref +
                          "\nVenez récupérer dans 2h.");
            }
            default -> end("Option invalide. Recomposez *338#.");
        };
    }

    // ── Search flow ───────────────────────────────────────────────────────────

    private String searchResults(String sid, String query) {
        if (query.isBlank() || query.length() < 3) {
            return con("Nom trop court (3 caractères min).\nEntrez le nom:");
        }

        List<MedicationSearchResultDto> results =
            searchService.search(query, null, null, 10.0);

        if (results.isEmpty()) {
            return end("Aucun résultat pour '" + query + "'.\n" +
                       "Recomposez *338# pour réessayer.");
        }

        // Cache flat list of (medicationId, name, pharmacyId, pharmacyName, price) for later steps
        List<UssdStockItem> items = results.stream()
            .flatMap(r -> r.getPharmacies().stream()
                .filter(p -> p.getQuantity() > 0)
                .map(p -> new UssdStockItem(
                    r.getId().toString(), r.getName(),
                    p.getPharmacyId().toString(), p.getPharmacyName(),
                    p.getUnitPrice(), p.getDistanceKm()
                ))
            )
            .limit(MAX_RESULTS)
            .toList();

        if (items.isEmpty()) {
            return end("'" + query + "' trouvé mais en rupture\ndans les pharmacies proches.");
        }

        session.store(sid, "search_items", items);

        var sb = new StringBuilder("Résultats pour '").append(query).append("':\n");
        for (int i = 0; i < items.size(); i++) {
            UssdStockItem it = items.get(i);
            sb.append(i + 1).append(". ")
              .append(truncate(it.pharmacyName(), 16))
              .append(" – ").append((int) it.price()).append(" FCFA");
            if (it.distanceKm() > 0) {
                sb.append(" (").append(String.format("%.1f", it.distanceKm())).append("km)");
            }
            sb.append("\n");
        }
        sb.append("0. Retour");
        return con(sb.toString());
    }

    private String pharmacyConfirm(String sid, String query, String idxStr) {
        int idx = parseIdx(idxStr);
        List<UssdStockItem> items = session.loadList(sid, "search_items",
            new TypeReference<>() {});

        if (items.isEmpty() || idx < 1 || idx > items.size()) {
            return end("Option invalide. Recomposez *338#.");
        }
        UssdStockItem item = items.get(idx - 1);

        return con("Confirmer la réservation ?\n" +
                   truncate(item.medicationName(), 20) + "\n" +
                   truncate(item.pharmacyName(), 20) + "\n" +
                   "Montant: " + (int) item.price() + " FCFA\n" +
                   "1. Confirmer\n2. Annuler");
    }

    private String doCreateReservation(String sid, String phone, String query, String idxStr) {
        int idx = parseIdx(idxStr);
        List<UssdStockItem> items = session.loadList(sid, "search_items",
            new TypeReference<>() {});

        if (items.isEmpty() || idx < 1 || idx > items.size()) {
            return end("Session expirée. Recomposez *338#.");
        }
        UssdStockItem item = items.get(idx - 1);

        Optional<User> userOpt = userRepository.findByPhone(phone);
        if (userOpt.isEmpty()) {
            return end("Numéro non enregistré sur Medoq.\nInscrivez-vous sur l'application.");
        }
        User user = userOpt.get();

        try {
            var itemReq = ReservationItemRequest.builder()
                .medicationId(UUID.fromString(item.medicationId()))
                .quantity(1)
                .build();
            var req = CreateReservationRequest.builder()
                .pharmacyId(UUID.fromString(item.pharmacyId()))
                .items(List.of(itemReq))
                .build();

            Reservation r = reservationService.create(req, user.getId());
            session.store(sid, "created_reservation_id", r.getId().toString());

            return con("✅ Réservation créée !\nRéf: " + r.getReference() +
                       "\nPayer par Orange Money ?\n1. Payer maintenant\n2. Plus tard");
        } catch (Exception e) {
            log.warn("USSD reservation create failed: {}", e.getMessage());
            return end("Impossible de créer la réservation.\n" +
                       "Stock insuffisant ou session expirée.");
        }
    }

    private String doPayOrangeMoney(String sid, String phone, String reservationId) {
        Optional<User> userOpt = userRepository.findByPhone(phone);
        if (userOpt.isEmpty()) return end("Utilisateur introuvable.");

        try {
            UUID resId = UUID.fromString(reservationId);
            UUID userId = userOpt.get().getId();
            paymentService.initiateOrange(resId, userId);
            session.clear(sid);
            return end("💰 Paiement Orange Money initié !\nConfirmez sur votre téléphone.\n" +
                       "Merci d'utiliser Medoq.");
        } catch (Exception e) {
            log.warn("USSD Orange Money init failed: {}", e.getMessage());
            return end("Erreur de paiement. Réessayez\ndepuis l'application Medoq.");
        }
    }

    // ── My reservations flow ──────────────────────────────────────────────────

    private String myReservationsList(String sid, String phone) {
        Optional<User> userOpt = userRepository.findByPhone(phone);
        if (userOpt.isEmpty()) {
            return end("Numéro non enregistré.\nInscrivez-vous sur l'application.");
        }

        List<Reservation> active = reservationRepository
            .findByCustomerIdOrderByCreatedAtDesc(userOpt.get().getId())
            .stream()
            .filter(r -> !r.getStatus().isTerminal())
            .limit(MAX_RESERVATIONS)
            .toList();

        if (active.isEmpty()) {
            return end("Aucune réservation en cours.\nComposez *338# pour rechercher.");
        }

        List<String> refs = active.stream().map(Reservation::getId).map(UUID::toString).toList();
        session.store(sid, "reservation_ids", refs);

        var sb = new StringBuilder("Mes réservations:\n");
        for (int i = 0; i < active.size(); i++) {
            Reservation r = active.get(i);
            sb.append(i + 1).append(". ")
              .append(r.getReference()).append("\n   ")
              .append(statusFr(r.getStatus())).append("\n");
        }
        sb.append("0. Retour");
        return con(sb.toString());
    }

    private String myReservationDetail(String sid, String idxStr) {
        int idx = parseIdx(idxStr);
        List<String> ids = session.loadList(sid, "reservation_ids",
            new TypeReference<>() {});

        if (ids.isEmpty() || idx < 1 || idx > ids.size()) {
            return end("Option invalide. Recomposez *338#.");
        }
        String reservationId = ids.get(idx - 1);

        return reservationRepository.findById(UUID.fromString(reservationId))
            .map(r -> {
                String expiry = r.getExpiresAt() != null
                    ? "\nExpire: " + r.getExpiresAt().toString().substring(11, 16) + " UTC"
                    : "";
                return end("Réf: " + r.getReference() +
                           "\nStatut: " + statusFr(r.getStatus()) +
                           "\nPharmacien: " + truncate(r.getPharmacy().getName(), 18) +
                           "\nMontant: " + r.getTotalAmount().toPlainString() + " FCFA" +
                           expiry);
            })
            .orElse(end("Réservation introuvable."));
    }

    // ── Cancel flow ───────────────────────────────────────────────────────────

    private String cancelConfirm(String phone, String ref) {
        Optional<User> userOpt = userRepository.findByPhone(phone);
        if (userOpt.isEmpty()) return end("Numéro non enregistré.");

        return reservationRepository.findByReference(ref.trim().toUpperCase())
            .map(r -> {
                if (!r.getCustomer().getPhone().equals(phone)) {
                    return end("Réservation non trouvée.");
                }
                if (r.getStatus().isTerminal()) {
                    return end("Réservation déjà " + statusFr(r.getStatus()) + ".\nImpossible d'annuler.");
                }
                return con("Annuler cette réservation ?\n" +
                           r.getReference() + "\n" +
                           truncate(r.getPharmacy().getName(), 18) + "\n" +
                           r.getTotalAmount().toPlainString() + " FCFA\n" +
                           "1. Confirmer l'annulation\n2. Retour");
            })
            .orElse(end("Réservation '" + ref + "' introuvable.\nVérifiez la référence."));
    }

    private String doCancelReservation(String phone, String ref) {
        Optional<User> userOpt = userRepository.findByPhone(phone);
        if (userOpt.isEmpty()) return end("Numéro non enregistré.");

        return reservationRepository.findByReference(ref.trim().toUpperCase())
            .map(r -> {
                if (!r.getCustomer().getPhone().equals(phone)) {
                    return end("Réservation non trouvée.");
                }
                try {
                    reservationService.cancel(r.getId(), userOpt.get().getId(), "Annulé via USSD");
                    return end("✅ Réservation " + r.getReference() + " annulée.\nMerci d'utiliser Medoq.");
                } catch (Exception e) {
                    return end("Impossible d'annuler.\n" + e.getMessage());
                }
            })
            .orElse(end("Réservation introuvable."));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String con(String text) { return "CON " + text; }
    private String end(String text) { return "END " + text; }

    private int parseIdx(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private String statusFr(Reservation.Status s) {
        return switch (s) {
            case PENDING   -> "En attente";
            case CONFIRMED -> "Confirmée";
            case PAID      -> "Payée";
            case READY     -> "Prête ✅";
            case COMPLETED -> "Terminée";
            case CANCELLED -> "Annulée";
            case EXPIRED   -> "Expirée";
        };
    }

    // ── Value object for cached search results ────────────────────────────────

    public record UssdStockItem(
        String medicationId,
        String medicationName,
        String pharmacyId,
        String pharmacyName,
        double price,
        double distanceKm
    ) {}
}
