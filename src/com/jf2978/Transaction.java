package com.jf2978;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Transaction {

    // Static Variables
    private static int total = 0; // number of transactions generated, to be used within our hash to differentiate duplicates (think CTR Mode of operation)

    //Instance Variables
    String id; // TX hash for this transaction
    public PublicKey sender;
    public PublicKey recipient;
    public float value; // amount to send
    public byte[] signature; // digital signature to verify

    public Set<TransactionOutput> inputs; // previous transaction outputs of sender (to be spent for this transaction)
    public Set<TransactionOutput> outputs; // resulting transaction outputs (including change)

    // Constructor(s)
    // Input + Output Transaction constructor
    public Transaction(PublicKey from, PublicKey to, float val, Set<TransactionOutput> in){
        sender = from;
        recipient = to;
        value = val;
        inputs = in;
        outputs = new HashSet<>();
        id = hash();
    }

    // Genesis Transaction - Hardcoded id, auto-generate Transaction output since there are no inputs to process
    public Transaction(PublicKey from, PublicKey to, float val){
        id = "0";
        sender = from;
        recipient = to;
        value = val;
        outputs = new HashSet<>();
        outputs.add(new TransactionOutput(recipient, value, id));
    }

    // Generates cryptographic (unique) id for this transaction using SHA-512 hash
    private String hash(){
        total++;
        return Utility.SHA512(this.simplify());
    }

    // Generates signature of the hash of this transaction (providing data integrity)
    public void sign(PrivateKey dK){
        signature = Utility.ECDSASignature(dK, id);
    }

    // Verifies transaction signature
    public boolean verify(){
        return Utility.verifyECDSASignature(sender, id, signature);
    }

    public boolean process(){

        // Check Unspent transactions map
        Map<PublicKey, Set<TransactionOutput>> UTXOs = Main.SimpleBlockChain.UTXOs;

        // Verify the signature of this transaction
        if(!verify()){
            System.out.println("Failed to verify transaction signature...");
            return false;
        }

        // Check that inputs correspond with what's in the blockchain
        if(!UTXOs.get(sender).containsAll(inputs)){
            System.out.println("Transaction inputs are invalid...");
            return false;
        }

        // check if inputs sum to a value large enough to process transaction amount
        // TODO: float fee = Utility.calculateFee(value);
        float sum = getInputsValue();
        if(sum < value) {
            System.out.printf("Available input (%f) too low for amount (%f)\n", sum, value);
            return false;
        }

        System.out.printf("Alice -> Bob Outputs: %s", outputs);

        // Generate TransactionOutput(s)
        float change = sum - value;
        outputs.add(new TransactionOutput(recipient, value, id));
        System.out.printf("Alice -> Bob Outputs: %s", outputs);

        // Potentially do something with transaction fee here
        if(change > 0){
            outputs.add(new TransactionOutput(sender, change, id));
        }
        System.out.printf("Alice -> Bob Outputs: %s", outputs);

        // Remove spent Transaction Outputs from UTXOs map
        UTXOs.get(sender).removeAll(inputs);
        System.out.printf("Alice's Wallet: %s", Main.SimpleBlockChain.UTXOs.get(sender));

        // Update blockchain with this set (and initializes entry if DNE for appropriate key)
        for(TransactionOutput output : outputs){
            UTXOs.putIfAbsent(output.recipient, new HashSet<>());
            UTXOs.get(output.recipient).add(output);
        }

        System.out.printf("Alice's Wallet: %s\n", Main.SimpleBlockChain.UTXOs.get(sender));
        return true;
    }

    // returns sum of inputs(UTXOs) values
    public float getInputsValue() {
        float total = 0;
        for(TransactionOutput i : inputs) {
            total += i.value;
        }
        return total;
    }

    // Method for compressing Transaction information as String (for signing and verifying)
    private String simplify(){
        return Utility.getStringFromKey(sender) + Utility.getStringFromKey(recipient) + Float.toString(value) + total;
    }

    // Method for returning human-readable form of this transaction
    public String toString(){
        return new GsonBuilder().setPrettyPrinting().create().toJson(this);
    }
}