# Consultation Module

This module provides abstractions and implementations for validating certificate chains against trust anchors. 
It follows a layered approach, from high-level context-aware validation specific to EUDI Wallet 
to low-level chain validation.

## Key Components

```mermaid
classDiagram
    class IsChainTrustedForContext 
    class VerificationContext 
    class ValidateCertificateChain 
    class GetTrustAnchors
    <<interface>> GetTrustAnchors
    <<interface>> ValidateCertificateChain
    <<enum>> VerificationContext
    IsChainTrustedForContext -- "*" VerificationContext : supports
    IsChainTrustedForContext --> ValidateCertificateChain: uses
    VerificationContext -- "1" GetTrustAnchors: associated with
        
```

### IsChainTrustedForContext

`IsChainTrustedForContext` is the high-level entry point for certificate chain validation. 
It acts as an aggregation of `GetTrustAnchors` instances, organized per `VerificationContext`. 
It allows callers to validate a certificate chain within a specific context (e.g., verifying a PID or a Wallet Instance Attestation).

### VerificationContext

`VerificationContext` defines the various scenarios where certificate validation is required. Each context represents a specific use case, such as:
- `WalletInstanceAttestation`
- `WalletUnitAttestation`
- `PID`
- `PubEAA`
- and more.

### GetTrustAnchors

`GetTrustAnchors` is a functional interface responsible for retrieving a list of trust anchors.

### ValidateCertificateChain

`ValidateCertificateChain` is a low-level abstraction responsible for the technical validation of a certificate chain. 
It assumes that a set of trust anchors is already provided and performs 
the necessary checks (signature verification, path validation, etc.) 
to determine if the chain is valid, according to those anchors.

## Platform Support

The library provides specific implementations for JVM and Android targets:

- An implementation of `ValidateCertificateChain` based on the Java Security API.
- A factory method to obtain an `GetTrustAnchors` instance from a `KeyStore`.



