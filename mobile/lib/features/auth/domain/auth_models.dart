import 'package:flutter/foundation.dart';

@immutable
class AuthUser {
  final String id;
  final String phone;
  final String firstName;
  final String lastName;
  final String? email;
  final String role;

  const AuthUser({
    required this.id,
    required this.phone,
    required this.firstName,
    required this.lastName,
    this.email,
    required this.role,
  });

  factory AuthUser.fromJson(Map<String, dynamic> json) => AuthUser(
    id:        json['id'] as String,
    phone:     json['phone'] as String,
    firstName: json['firstName'] as String,
    lastName:  json['lastName'] as String,
    email:     json['email'] as String?,
    role:      json['role'] as String,
  );

  String get fullName => '$firstName $lastName';
}

@immutable
class AuthTokens {
  final String accessToken;
  final String refreshToken;
  final AuthUser user;

  const AuthTokens({
    required this.accessToken,
    required this.refreshToken,
    required this.user,
  });

  factory AuthTokens.fromJson(Map<String, dynamic> json) => AuthTokens(
    accessToken:  json['accessToken'] as String,
    refreshToken: json['refreshToken'] as String,
    user:         AuthUser.fromJson(json['user'] as Map<String, dynamic>),
  );
}
