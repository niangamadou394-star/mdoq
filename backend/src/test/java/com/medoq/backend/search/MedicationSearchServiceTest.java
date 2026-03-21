package com.medoq.backend.search;

import com.medoq.backend.dto.search.PharmacyNearbyDto;
import com.medoq.backend.dto.search.SearchResponse;
import com.medoq.backend.entity.Medication;
import com.medoq.backend.exception.ResourceNotFoundException;
import com.medoq.backend.repository.MedicationRepository;
import com.medoq.backend.repository.PharmacyRepository;
import com.medoq.backend.repository.projection.MedicationSearchRow;
import com.medoq.backend.repository.projection.PharmacyNearbyRow;
import com.medoq.backend.service.MedicationSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicationSearchServiceTest {

    @Mock MedicationRepository medicationRepository;
    @Mock PharmacyRepository   pharmacyRepository;

    @InjectMocks MedicationSearchService service;

    // ── search — no geo ───────────────────────────────────────────

    @Test
    void search_noGeo_callsTextSearch() {
        when(medicationRepository.searchWithoutGeo(eq("paracetamol"), anyInt()))
                .thenReturn(List.of());

        SearchResponse resp = service.search("paracetamol", null, null, null);

        assertThat(resp.query()).isEqualTo("paracetamol");
        assertThat(resp.total()).isZero();
        verify(medicationRepository).searchWithoutGeo(eq("paracetamol"), anyInt());
        verifyNoMoreInteractions(medicationRepository);
    }

    @Test
    void search_withGeo_callsGeoSearch() {
        when(medicationRepository.searchWithGeo(
                eq("amoxicilline"), eq(14.6937), eq(-17.4441), anyDouble(), anyInt()))
                .thenReturn(List.of());

        service.search("amoxicilline", 14.6937, -17.4441, 5.0);

        verify(medicationRepository)
                .searchWithGeo(eq("amoxicilline"), eq(14.6937), eq(-17.4441),
                               eq(5000.0), anyInt());
    }

    @Test
    void search_groupsRowsByMedication() {
        MedicationSearchRow row1 = buildSearchRow("med-1", "Pharma A", 1.0, 50, 10);
        MedicationSearchRow row2 = buildSearchRow("med-1", "Pharma B", 2.0, 5,  10);
        MedicationSearchRow row3 = buildSearchRow("med-2", "Pharma C", 0.5, 20, 10);

        when(medicationRepository.searchWithoutGeo(any(), anyInt()))
                .thenReturn(List.of(row1, row2, row3));

        SearchResponse resp = service.search("test", null, null, null);

        assertThat(resp.total()).isEqualTo(2);
        assertThat(resp.results().get(0).pharmacies()).hasSize(2);
        assertThat(resp.results().get(1).pharmacies()).hasSize(1);
    }

    // ── getById ───────────────────────────────────────────────────

    @Test
    void getById_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(medicationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_found_returnsDetail() {
        UUID id = UUID.randomUUID();
        Medication med = Medication.builder()
                .id(id).name("Doliprane").genericName("Paracétamol")
                .requiresPrescription(false).build();

        when(medicationRepository.findById(id)).thenReturn(Optional.of(med));
        when(medicationRepository.findDetailStockByMedicationId(id.toString()))
                .thenReturn(List.of());

        var detail = service.getById(id);
        assertThat(detail.name()).isEqualTo("Doliprane");
        assertThat(detail.availability()).isEmpty();
    }

    // ── findNearbyPharmacies ──────────────────────────────────────

    @Test
    void findNearby_withMedication_callsCorrectQuery() {
        UUID medId = UUID.randomUUID();
        when(pharmacyRepository.findNearbyWithMedication(
                anyDouble(), anyDouble(), anyDouble(), eq(medId.toString())))
                .thenReturn(List.of());

        List<PharmacyNearbyDto> result =
                service.findNearbyPharmacies(14.69, -17.44, medId, 3.0);

        assertThat(result).isEmpty();
        verify(pharmacyRepository)
                .findNearbyWithMedication(14.69, -17.44, 3000.0, medId.toString());
        verify(pharmacyRepository, never()).findNearby(anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void findNearby_withoutMedication_callsAllNearby() {
        when(pharmacyRepository.findNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());

        service.findNearbyPharmacies(14.69, -17.44, null, null);

        verify(pharmacyRepository).findNearby(14.69, -17.44, 5000.0);
        verify(pharmacyRepository, never())
                .findNearbyWithMedication(anyDouble(), anyDouble(), anyDouble(), any());
    }

    // ── helpers ───────────────────────────────────────────────────

    private MedicationSearchRow buildSearchRow(
            String medId, String pharmName, double distKm, int qty, int reorder) {
        return new MedicationSearchRow() {
            public String  getMedId()                { return medId; }
            public String  getMedName()               { return "Med-" + medId; }
            public String  getGenericName()           { return null; }
            public String  getBrandName()             { return null; }
            public String  getDci()                   { return null; }
            public String  getCategory()              { return null; }
            public String  getDosageForm()            { return null; }
            public String  getStrength()              { return null; }
            public Boolean getRequiresPrescription()  { return false; }
            public String  getImageUrl()              { return null; }
            public String  getPharmId()               { return UUID.randomUUID().toString(); }
            public String  getPharmName()             { return pharmName; }
            public String  getAddress()               { return "123 Rue test"; }
            public String  getCity()                  { return "Dakar"; }
            public Double  getLatitude()              { return 14.69; }
            public Double  getLongitude()             { return -17.44; }
            public String  getOpeningHoursJson()      { return null; }
            public Boolean getIs24h()                 { return false; }
            public Double  getRating()                { return 4.0; }
            public Integer getQuantity()              { return qty; }
            public BigDecimal getUnitPrice()          { return BigDecimal.valueOf(500); }
            public Integer getReorderLevel()          { return reorder; }
            public Double  getDistanceKm()            { return distKm; }
        };
    }
}
