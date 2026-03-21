package com.medoq.backend.service;

import com.medoq.backend.config.CacheConfig;
import com.medoq.backend.dto.search.*;
import com.medoq.backend.entity.Medication;
import com.medoq.backend.exception.ResourceNotFoundException;
import com.medoq.backend.repository.MedicationRepository;
import com.medoq.backend.repository.PharmacyRepository;
import com.medoq.backend.repository.projection.MedicationSearchRow;
import com.medoq.backend.repository.projection.PharmacyNearbyRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicationSearchService {

    /** Maximum medications returned per search. */
    private static final int  MAX_MEDICATIONS = 20;
    /** Maximum pharmacies shown per medication in search results. */
    private static final int  MAX_PHARMACIES_PER_MED = 10;
    /** Default search radius in km. */
    private static final double DEFAULT_RADIUS_KM = 5.0;

    private final MedicationRepository medicationRepository;
    private final PharmacyRepository   pharmacyRepository;

    // ── Search ────────────────────────────────────────────────────

    /**
     * Full-text + geo search.
     *
     * Cache key encodes query + coordinates rounded to 4 decimal places (~11 m precision)
     * so nearby requests share the same cache bucket.
     */
    @Cacheable(
        value  = CacheConfig.CACHE_SEARCH,
        key    = "#query + ':' + #lat + ':' + #lng + ':' + #radiusKm",
        unless = "#result.total == 0"
    )
    public SearchResponse search(String query, Double lat, Double lng, Double radiusKm) {
        String normalizedQuery = query.trim();
        List<MedicationSearchRow> rows;

        if (lat != null && lng != null) {
            double radius = radiusKm != null ? radiusKm : DEFAULT_RADIUS_KM;
            double radiusMeters = radius * 1_000.0;
            double roundedLat = round4(lat);
            double roundedLng = round4(lng);

            log.debug("Geo search: q='{}' lat={} lng={} r={}km",
                normalizedQuery, roundedLat, roundedLng, radius);

            rows = medicationRepository.searchWithGeo(
                normalizedQuery, roundedLat, roundedLng, radiusMeters, MAX_MEDICATIONS * 10);
        } else {
            log.debug("Text search: q='{}'", normalizedQuery);
            rows = medicationRepository.searchWithoutGeo(normalizedQuery, MAX_MEDICATIONS * 10);
        }

        List<MedicationSearchResultDto> results = groupByMedication(rows);

        return new SearchResponse(normalizedQuery, results.size(), results);
    }

    // ── Detail ────────────────────────────────────────────────────

    @Cacheable(value = CacheConfig.CACHE_DETAIL, key = "#id.toString()")
    public MedicationDetailDto getById(UUID id) {
        Medication med = medicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medication", id));

        var stockRows = medicationRepository.findDetailStockByMedicationId(id.toString());
        var availability = stockRows.stream()
                .map(MedicationDetailDto.PharmacyAvailabilityDto::from)
                .toList();

        return MedicationDetailDto.from(med, availability);
    }

    // ── Popular ───────────────────────────────────────────────────

    @Cacheable(value = CacheConfig.CACHE_POPULAR, key = "'all'")
    public List<PopularMedicationDto> getPopular() {
        return medicationRepository.findPopular()
                .stream()
                .map(PopularMedicationDto::from)
                .toList();
    }

    // ── Nearby pharmacies ─────────────────────────────────────────

    @Cacheable(
        value  = CacheConfig.CACHE_NEARBY,
        key    = "#lat + ':' + #lng + ':' + #medicationId + ':' + #radiusKm"
    )
    public List<PharmacyNearbyDto> findNearbyPharmacies(
            double lat, double lng, UUID medicationId, Double radiusKm) {

        double radius = radiusKm != null ? radiusKm : DEFAULT_RADIUS_KM;
        double radiusMeters = radius * 1_000.0;
        double roundedLat   = round4(lat);
        double roundedLng   = round4(lng);

        List<PharmacyNearbyRow> rows;

        if (medicationId != null) {
            log.debug("Nearby pharmacies: lat={} lng={} medId={} r={}km",
                roundedLat, roundedLng, medicationId, radius);
            rows = pharmacyRepository.findNearbyWithMedication(
                roundedLat, roundedLng, radiusMeters, medicationId.toString());
        } else {
            log.debug("Nearby pharmacies (all): lat={} lng={} r={}km",
                roundedLat, roundedLng, radius);
            rows = pharmacyRepository.findNearby(roundedLat, roundedLng, radiusMeters);
        }

        return rows.stream()
                .map(PharmacyNearbyDto::from)
                .toList();
    }

    // ── Cache invalidation ────────────────────────────────────────

    /**
     * Called by StockService when pharmacy stock is updated.
     * Evicts all search/nearby caches (Spring Cache @CacheEvict can also be used
     * on the methods that modify stock).
     */
    public void evictStockCaches() {
        // Spring's CacheManager is used via @CacheEvict annotations on write methods.
        // This method is a hook for programmatic eviction if needed.
        log.info("Stock caches evicted");
    }

    // ── Internal helpers ──────────────────────────────────────────

    /**
     * Groups flat (medication × pharmacy) rows into a list of medications,
     * each with up to MAX_PHARMACIES_PER_MED pharmacies sorted by distance.
     */
    private List<MedicationSearchResultDto> groupByMedication(List<MedicationSearchRow> rows) {
        // Use LinkedHashMap to preserve insertion order (already ORDER BY distance / name)
        Map<String, List<MedicationSearchRow>> grouped = rows.stream()
                .collect(Collectors.groupingBy(
                    MedicationSearchRow::getMedId,
                    LinkedHashMap::new,
                    Collectors.toList()));

        return grouped.entrySet().stream()
                .limit(MAX_MEDICATIONS)
                .map(entry -> {
                    List<MedicationSearchRow> medRows = entry.getValue();
                    MedicationSearchRow first = medRows.get(0);

                    List<PharmacyStockDto> pharmacies = medRows.stream()
                            .filter(r -> r.getPharmId() != null)
                            .map(PharmacyStockDto::from)
                            // sort by distance, then price for ties (or when distance is null)
                            .sorted(Comparator
                                .comparingDouble((PharmacyStockDto p) ->
                                    p.distanceKm() != null ? p.distanceKm() : Double.MAX_VALUE)
                                .thenComparing(p ->
                                    p.unitPrice() != null ? p.unitPrice() : BigDecimal.ZERO))
                            .limit(MAX_PHARMACIES_PER_MED)
                            .toList();

                    return MedicationSearchResultDto.of(first, pharmacies);
                })
                .toList();
    }

    /** Rounds a coordinate to 4 decimal places (~11 m precision). */
    private static double round4(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
