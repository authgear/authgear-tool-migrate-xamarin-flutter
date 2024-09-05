library migratetool;

class AuthgearException implements Exception {
  final Exception? underlyingException;
  const AuthgearException(this.underlyingException);
  @override
  String toString() {
    return "Underlying: $underlyingException";
  }
}
