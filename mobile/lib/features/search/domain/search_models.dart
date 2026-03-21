import 'package:flutter/foundation.dart';

// ── Stock badge ───────────────────────────────────────────────────────────────

enum StockBadge { available, limited, outOfStock }

extension StockBadgeExt on StockBadge {
  static StockBadge fromString(String s) => switch (s) {
    'AVAILABLE'    => StockBadge.available,
    'LIMITED'      => StockBadge.limited,
    'OUT_OF_STOCK' => StockBadge.outOfStock,
    _              => StockBadge.outOfStock,
  };

  String get label => switch (this) {
    StockBadge.available   => 'Disponible',
    StockBadge.limited     => 'Limité',
    StockBadge.outOfStock  => 'Rupture',
  };
}

// ── Pharmacy stock DTO ────────────────────────────────────────────────────────

@immutable
class PharmacyStockDto {
  final String    pharmacyId;
  final String    pharmacyName;
  final String    address;
  final String    city;
  final String    phone;
  final double    latitude;
  final double    longitude;
  final double    distanceKm;
  final int       quantity;
  final double    unitPrice;
  final StockBadge badge;
  final Map<String, String>? openingHours;

  const PharmacyStockDto({
    required this.pharmacyId,
    required this.pharmacyName,
    required this.address,
    required this.city,
    required this.phone,
    required this.latitude,
    required this.longitude,
    required this.distanceKm,
    required this.quantity,
    required this.unitPrice,
    required this.badge,
    this.openingHours,
  });

  factory PharmacyStockDto.fromJson(Map<String, dynamic> j) => PharmacyStockDto(
    pharmacyId:   j['pharmacyId']   as String,
    pharmacyName: j['pharmacyName'] as String,
    address:      j['address']      as String,
    city:         j['city']         as String,
    phone:        j['phone']        as String,
    latitude:     (j['latitude']    as num).toDouble(),
    longitude:    (j['longitude']   as num).toDouble(),
    distanceKm:   (j['distanceKm']  as num).toDouble(),
    quantity:     j['quantity']     as int,
    unitPrice:    (j['unitPrice']   as num).toDouble(),
    badge:        StockBadgeExt.fromString(j['stockBadge'] as String),
    openingHours: (j['openingHours'] as Map<String, dynamic>?)?.map(
      (k, v) => MapEntry(k, v as String),
    ),
  );
}

// ── Medication search result ──────────────────────────────────────────────────

@immutable
class MedicationSearchResult {
  final String   id;
  final String   name;
  final String?  genericName;
  final String?  category;
  final String?  form;
  final String?  strength;
  final List<PharmacyStockDto> pharmacies;

  const MedicationSearchResult({
    required this.id,
    required this.name,
    this.genericName,
    this.category,
    this.form,
    this.strength,
    required this.pharmacies,
  });

  factory MedicationSearchResult.fromJson(Map<String, dynamic> j) =>
      MedicationSearchResult(
        id:          j['id']          as String,
        name:        j['name']        as String,
        genericName: j['genericName'] as String?,
        category:    j['category']    as String?,
        form:        j['form']        as String?,
        strength:    j['strength']    as String?,
        pharmacies: (j['pharmacies'] as List<dynamic>)
            .map((e) => PharmacyStockDto.fromJson(e as Map<String, dynamic>))
            .toList(),
      );

  StockBadge get bestBadge {
    if (pharmacies.any((p) => p.badge == StockBadge.available)) {
      return StockBadge.available;
    }
    if (pharmacies.any((p) => p.badge == StockBadge.limited)) {
      return StockBadge.limited;
    }
    return StockBadge.outOfStock;
  }

  double? get minPrice {
    if (pharmacies.isEmpty) return null;
    return pharmacies.map((p) => p.unitPrice).reduce(
      (a, b) => a < b ? a : b,
    );
  }

  double? get minDistanceKm {
    if (pharmacies.isEmpty) return null;
    return pharmacies.map((p) => p.distanceKm).reduce(
      (a, b) => a < b ? a : b,
    );
  }
}

// ── Popular medication ────────────────────────────────────────────────────────

@immutable
class PopularMedication {
  final String id;
  final String name;
  final String? category;
  final int reservationCount;

  const PopularMedication({
    required this.id,
    required this.name,
    this.category,
    required this.reservationCount,
  });

  factory PopularMedication.fromJson(Map<String, dynamic> j) =>
      PopularMedication(
        id:               j['id']               as String,
        name:             j['name']             as String,
        category:         j['category']         as String?,
        reservationCount: j['reservationCount'] as int,
      );
}
