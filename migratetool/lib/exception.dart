library migratetool;

class AuthgearException implements Exception {
  final Exception? underlyingException;
  const AuthgearException(this.underlyingException);
}
