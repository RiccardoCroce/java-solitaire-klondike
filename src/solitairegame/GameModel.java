package solitairegame;

import java.util.*;

/**
 * MODELLO - Contiene lo stato del gioco e la logica di business.
 * Non ha dipendenze da Swing o dalla Vista.
 */
public class GameModel {

    // ── Carta ────────────────────────────────────────────────────────────────

    public static class Card {

        /** Seme della carta */
        public enum Suit {
            HEARTS("♥"), DIAMONDS("♦"), CLUBS("♣"), SPADES("♠");
            private final String simbolo;
            Suit(String simbolo) { this.simbolo = simbolo; }
            @Override public String toString() { return simbolo; }
        }

        /** Valore/rango della carta */
        public enum Rank {
            ACE("A"), TWO("2"), THREE("3"), FOUR("4"), FIVE("5"), SIX("6"),
            SEVEN("7"), EIGHT("8"), NINE("9"), TEN("10"), JACK("J"), QUEEN("Q"), KING("K");
            private final String simbolo;
            Rank(String simbolo) { this.simbolo = simbolo; }
            @Override public String toString() { return simbolo; }
        }

        private final Suit seme;
        private final Rank rango;
        private boolean    facciaInSu;

        public Card(Suit seme, Rank rango) {
            this.seme       = seme;
            this.rango      = rango;
            this.facciaInSu = false;
        }

        public Suit    getSuit()  { return seme;       }
        public Rank    getRank()  { return rango;      }
        public boolean isFaceUp() { return facciaInSu; }
        /** Gira la carta (faccia su <-> faccia giu). */
        public void    flip()     { facciaInSu = !facciaInSu; }
    }

    // ── Mazzo ────────────────────────────────────────────────────────────────

    public static class Deck {
        private final List<Card> carte = new ArrayList<>();

        /** Crea un mazzo completo di 52 carte. */
        public Deck() {
            for (Card.Suit seme : Card.Suit.values())
                for (Card.Rank rango : Card.Rank.values())
                    carte.add(new Card(seme, rango));
        }

        /** Mescola il mazzo. */
        public void shuffle()     { Collections.shuffle(carte); }

        /** Ritorna true se ci sono ancora carte nel mazzo. */
        public boolean hasCards() { return !carte.isEmpty(); }

        /** Pesca la carta in cima al mazzo e la rimuove. */
        public Card draw() {
            return carte.isEmpty() ? null : carte.remove(carte.size() - 1);
        }
    }

    // ── Livello di difficolta ─────────────────────────────────────────────────

    public enum Difficulty { FACILE, DIFFICILE }

    // ── Stato del gioco ──────────────────────────────────────────────────────

    private Deck             mazzo;
    private List<Card>       pilaStock;       // carte ancora da pescare
    private List<Card>       pilaScarto;      // carte gia pescate (waste)
    private List<List<Card>> fondamenta;      // 4 pile fondazione (A->K per seme)
    private List<List<Card>> tableau;         // 7 colonne di gioco

    private List<Card> carteTrascinate   = new ArrayList<>();
    private int        sorgentePila      = -1; // codice pila sorgente del drag
    private int        sorgentePosizione = -1; // indice carta nella colonna sorgente

    // Statistiche partita
    private int     secondiTrascorsi   = 0;
    private int     contatoreMovimenti = 0;
    private boolean partitaIniziata    = false;

    // Difficolta
    private Difficulty difficoltaCorrente      = Difficulty.FACILE;
    private int        carteDaPescareAllaVolta = 1; // 1 in FACILE, 3 in DIFFICILE

    // ── Costruttore ───────────────────────────────────────────────────────────

    /** Crea il modello con una partita gia' pronta (evita liste null). */
    public GameModel() {
        initGame();
    }

    // ── Inizializzazione partita ──────────────────────────────────────────────

    /** Inizializza o resetta una nuova partita. */
    public void initGame() {
        mazzo = new Deck();
        mazzo.shuffle();

        pilaStock  = new ArrayList<>();
        pilaScarto = new ArrayList<>();
        fondamenta = new ArrayList<>();
        tableau    = new ArrayList<>();

        secondiTrascorsi   = 0;
        contatoreMovimenti = 0;
        partitaIniziata    = false;

        // 4 fondamenta vuote e 7 colonne del tableau
        for (int i = 0; i < 4; i++) fondamenta.add(new ArrayList<>());
        for (int i = 0; i < 7; i++) tableau.add(new ArrayList<>());

        // Distribuisce le carte nel tableau:
        // colonna i ha (i+1) carte, solo l'ultima e' girata a faccia in su
        for (int col = 0; col < 7; col++) {
            for (int riga = 0; riga <= col; riga++) {
                Card carta = mazzo.draw();
                if (riga == col) carta.flip(); // ultima carta della colonna: faccia in su
                tableau.get(col).add(carta);
            }
        }

        // Le carte rimanenti vanno nello stock (tutte a faccia in giu)
        while (mazzo.hasCards()) pilaStock.add(mazzo.draw());

        carteTrascinate = new ArrayList<>();
    }

    // ── Pesca dallo stock ─────────────────────────────────────────────────────

    /**
     * Pesca dalla pila stock (click sullo stock).
     *
     * FACILE:    pesca sempre 1 carta alla volta.
     *            La waste mostra solo la carta in cima (1 carta visibile).
     *            Ogni click pesca la carta successiva nello stock, una alla volta.
     *
     * DIFFICILE: pesca 3 carte alla volta.
     *            La waste mostra le ultime 3 carte sovrapposte.
     *
     * Se lo stock e' vuoto, rimette tutte le carte della waste
     * nello stock (girate a faccia in giu). Questa operazione NON
     * incrementa il contatore delle mosse.
     */
    public void drawFromStock() {
        if (!pilaStock.isEmpty()) {
            // Pesca esattamente carteDaPescareAllaVolta carte (1 in facile, 3 in difficile)
            int daPescare = Math.min(carteDaPescareAllaVolta, pilaStock.size());
            for (int i = 0; i < daPescare; i++) {
                Card carta = pilaStock.remove(pilaStock.size() - 1);
                carta.flip(); // gira a faccia in su
                pilaScarto.add(carta);
            }
            incrementaMovimenti();
        } else if (!pilaScarto.isEmpty()) {
            // Stock esaurito: rimette tutta la waste nello stock, girata a faccia in giu
            while (!pilaScarto.isEmpty()) {
                Card carta = pilaScarto.remove(pilaScarto.size() - 1);
                carta.flip(); // gira a faccia in giu
                pilaStock.add(carta);
            }
            // Il riciclo dello stock NON conta come mossa
        }
    }

    /**
     * Restituisce quante carte della waste pile devono essere visibili nella Vista.
     *
     * La Vista DEVE usare questo metodo invece di assumere sempre 3.
     *
     * FACILE:    1 carta (solo quella in cima, la piu' recente).
     * DIFFICILE: fino a 3 carte (le ultime 3 sovrapposte con offset).
     */
    public int getCarteVisibiliWaste() {
        if (pilaScarto.isEmpty()) return 0;
        if (difficoltaCorrente == Difficulty.FACILE) return 1;
        return Math.min(3, pilaScarto.size());
    }

    // ── Regole di posizionamento ──────────────────────────────────────────────

    /** Ritorna true se la carta puo' essere posizionata sulla fondamenta indicata. */
    public boolean canPlaceOnFoundation(Card carta, int indiceFondamenta) {
        List<Card> f = fondamenta.get(indiceFondamenta);
        if (f.isEmpty()) return carta.getRank() == Card.Rank.ACE;
        Card cima = f.get(f.size() - 1);
        return carta.getSuit() == cima.getSuit() &&
                carta.getRank().ordinal() == cima.getRank().ordinal() + 1;
    }

    /** Ritorna true se la carta puo' essere posizionata sulla colonna tableau indicata. */
    public boolean canPlaceOnTableau(Card carta, int colonna) {
        List<Card> pila = tableau.get(colonna);
        if (pila.isEmpty()) return carta.getRank() == Card.Rank.KING;
        Card cima     = pila.get(pila.size() - 1);
        boolean cartaRossa = carta.getSuit() == Card.Suit.HEARTS || carta.getSuit() == Card.Suit.DIAMONDS;
        boolean cimaRossa  = cima.getSuit()  == Card.Suit.HEARTS || cima.getSuit()  == Card.Suit.DIAMONDS;
        return cartaRossa != cimaRossa &&
                carta.getRank().ordinal() == cima.getRank().ordinal() - 1;
    }

    // ── Posizionamento carte (drop) ───────────────────────────────────────────

    /**
     * Tenta di posizionare la carta trascinata su una fondamenta.
     * Ritorna true se l'operazione e' riuscita.
     * @param indiceFondamenta
     * @return
     */
    public boolean tryPlaceOnFoundation(int indiceFondamenta) {
        if (carteTrascinate.size() != 1) return false;
        Card carta = carteTrascinate.get(0);
        if (!canPlaceOnFoundation(carta, indiceFondamenta)) return false;
        rimuoviCarteDallaSorgente();
        fondamenta.get(indiceFondamenta).add(carta);
        incrementaMovimenti();
        return true;
    }

    /**
     * Tenta di posizionare le carte trascinate su una colonna del tableau.
     * Ritorna true se l'operazione e' riuscita.
     * @param colonna
     * @return
     */
    public boolean tryPlaceOnTableau(int colonna) {
        if (carteTrascinate.isEmpty()) return false;
        Card primaCarta = carteTrascinate.get(0);
        if (!canPlaceOnTableau(primaCarta, colonna)) return false;
        rimuoviCarteDallaSorgente();
        tableau.get(colonna).addAll(carteTrascinate);
        incrementaMovimenti();
        return true;
    }

    /** Ritorna true se tutte e 4 le fondamenta sono complete (13 carte = vittoria).
     * @return  */
    public boolean checkWin() {
        for (List<Card> f : fondamenta) if (f.size() != 13) return false;
        return true;
    }

    /**
     * Rimuove le carte trascinate dalla pila sorgente originale.
     * Va chiamato PRIMA di aggiungere le carte alla destinazione.
     */
    public void rimuoviCarteDallaSorgente() {
        if (sorgentePila == -2) {
            // Sorgente: waste pile
            if (!pilaScarto.isEmpty())
                pilaScarto.remove(pilaScarto.size() - 1);
        } else if (sorgentePila < -2) {
            // Sorgente: fondamenta (codice: -(indice+3))
            int idx = -(sorgentePila + 3);
            List<Card> f = fondamenta.get(idx);
            if (!f.isEmpty()) f.remove(f.size() - 1);
        } else if (sorgentePila >= 0) {
            // Sorgente: colonna tableau
            List<Card> pila = tableau.get(sorgentePila);
            pila.removeAll(carteTrascinate);
            // Se l'ultima carta rimasta e' coperta, la gira
            if (!pila.isEmpty() && !pila.get(pila.size() - 1).isFaceUp())
                pila.get(pila.size() - 1).flip();
        }
    }

    /** Alias per compatibilita' con il Controller. */
    public void removeCardFromSource() { rimuoviCarteDallaSorgente(); }

    // ── Gestione drag ─────────────────────────────────────────────────────────

    /** Inizia un drag dalla waste pile (ultima carta visibile). */
    public void startDragFromWaste() {
        if (pilaScarto.isEmpty()) return;
        carteTrascinate.add(pilaScarto.get(pilaScarto.size() - 1));
        sorgentePila = -2;
    }

    /** Inizia un drag da una fondamenta.
     * @param i */
    public void startDragFromFoundation(int i) {
        List<Card> f = fondamenta.get(i);
        if (f.isEmpty()) return;
        carteTrascinate.add(f.get(f.size() - 1));
        sorgentePila = -(i + 3);
    }

    /** Inizia un drag da una colonna del tableau (include tutte le carte sotto).
     * @param colonna
     * @param indiceCarta */
    public void startDragFromTableau(int colonna, int indiceCarta) {
        List<Card> pila = tableau.get(colonna);
        for (int j = indiceCarta; j < pila.size(); j++)
            carteTrascinate.add(pila.get(j));
        sorgentePila      = colonna;
        sorgentePosizione = indiceCarta;
    }

    /** Azzera lo stato del drag al termine di un'operazione. */
    public void clearDrag() {
        carteTrascinate.clear();
        sorgentePila      = -1;
        sorgentePosizione = -1;
    }

    // ── Timer / Statistiche ──────────────────────────────────────────────────

    /** Chiamare ogni secondo dal timer della Vista per incrementare il contatore. */
    public void tickTimer() {
        if (partitaIniziata && !checkWin()) secondiTrascorsi++;
    }

    /** Incrementa il numero di mosse e segna la partita come iniziata. */
    private void incrementaMovimenti() {
        if (!partitaIniziata) partitaIniziata = true;
        contatoreMovimenti++;
    }

    // ── Impostazione difficolta ───────────────────────────────────────────────

    /** Imposta la difficolta' e aggiorna le carte da pescare alla volta. */
    public void setDifficulty(Difficulty d) {
        difficoltaCorrente      = d;
        carteDaPescareAllaVolta = (d == Difficulty.FACILE) ? 1 : 3;
    }

    // ── Getter ───────────────────────────────────────────────────────────────

    public List<Card>       getStockPile()           { return pilaStock;          }
    public List<Card>       getWastePile()            { return pilaScarto;         }
    public List<List<Card>> getFoundations()          { return fondamenta;         }
    public List<List<Card>> getTableau()              { return tableau;            }
    public List<Card>       getDraggedCards()         { return carteTrascinate;    }
    public int              getSourceTableau()        { return sorgentePila;       }
    public int              getSourceIndex()          { return sorgentePosizione;  }
    public int              getElapsedSeconds()       { return secondiTrascorsi;   }
    public int              getMoveCount()            { return contatoreMovimenti; }
    public boolean          isGameStarted()           { return partitaIniziata;    }
    public Difficulty       getDifficulty()           { return difficoltaCorrente; }
    public int              getCardsToDrawFromStock() { return carteDaPescareAllaVolta; }
}