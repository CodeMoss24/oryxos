package com.oryxos.core.exception;

/** Thrown when a Profile references a provider name not found in the global configuration. */
public class ProviderNotFoundException extends RuntimeException {

  public ProviderNotFoundException(String providerName) {
    super("Provider not found: " + providerName);
  }
}
