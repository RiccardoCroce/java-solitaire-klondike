package solitairegame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * CONTROLLER - Collega Model e View.
 * Gestisce gli eventi utente, aggiorna il Model e ordina alla View di ridisegnarsi.
 */
public class GameController {

    private final GameModel model;
    private final GameView  view;

    // Stato drag (coordinate) — i dati delle carte stanno nel model
    private Point dragStart  = null;
    private Point dragOffset = new Point(0, 0); // distanza del mouse dall'angolo in alto a sinistra della carta

    // ── Costruttore ──────────────────────────────────────────────────────────

    public GameController(GameModel model, GameView view) {
        this.model = model;
        this.view  = view;

        // Collega il pannello al model per il rendering
        view.gamePanel.setModel(model);

        // Registra gli event listener
        registerMouseListeners();
        registerButtonListeners();
        startTimer();

        // Prima partita
        model.initGame();
        refreshView();
    }

    // ── Timer ────────────────────────────────────────────────────────────────

    private void startTimer() {
        javax.swing.Timer timer = new javax.swing.Timer(1000, e -> {
            model.tickTimer();
            view.updateTimerLabel(model.getElapsedSeconds());
        });
        timer.start();
    }

    // ── Listener pulsanti ────────────────────────────────────────────────────

    private void registerButtonListeners() {
        view.getDifficultyBox().addActionListener(e -> {
            GameModel.Difficulty nuova = view.getDifficultyBox().getSelectedIndex() == 0
                    ? GameModel.Difficulty.FACILE
                    : GameModel.Difficulty.DIFFICILE;

            // Riselezionare la stessa voce non deve azzerare la partita in corso
            if (nuova == model.getDifficulty()) return;

            model.setDifficulty(nuova);
            view.updateDifficultyLabel(nuova == GameModel.Difficulty.FACILE ? "Facile" : "Difficile");
            model.initGame();
            refreshView();
        });

        view.getNewGameButton().addActionListener(e -> {
            model.initGame();
            refreshView();
        });

        view.getWinButton().addActionListener(e -> showVictory());
    }

    // ── Listener mouse del GamePanel ─────────────────────────────────────────

    private void registerMouseListeners() {
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed (MouseEvent e) { handleMousePress(e);   }
            @Override public void mouseDragged (MouseEvent e) { handleMouseDrag(e);    }
            @Override public void mouseReleased(MouseEvent e) { handleMouseRelease(e); }
        };
        view.gamePanel.addMouseListener(ma);
        view.gamePanel.addMouseMotionListener(ma);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /** True se il punto (mx,my) e' dentro una carta con angolo in alto a sinistra (x,y). */
    private static boolean hitCard(int mx, int my, int x, int y) {
        return mx >= x && mx <= x + GameView.CARD_WIDTH &&
                my >= y && my <= y + GameView.CARD_HEIGHT;
    }

    // ── Press (click sullo stock oppure inizio drag) ─────────────────────────

    private void handleMousePress(MouseEvent e) {
        // Solo tasto sinistro: il destro/centrale non deve avviare drag o pescare
        if (!SwingUtilities.isLeftMouseButton(e)) return;

        int mx = e.getX(), my = e.getY();
        model.clearDrag();
        dragStart = null;

        // Stock: gestito qui e non in mouseClicked, che non scatta se il mouse
        // si muove anche di 1 pixel durante il click
        if (hitCard(mx, my, GameView.stockX(), GameView.CARD_SPACING)) {
            model.drawFromStock();
            refreshView();
            return;
        }

        // Waste
        if (!model.getWastePile().isEmpty()) {
            int show   = model.getCarteVisibiliWaste();
            int cardX  = GameView.wasteX() + (show - 1) * GameView.WASTE_FAN;
            if (hitCard(mx, my, cardX, GameView.CARD_SPACING)) {
                model.startDragFromWaste();
                beginDrag(e, mx - cardX, my - GameView.CARD_SPACING);
                return;
            }
        }

        // Foundations
        for (int i = 0; i < 4; i++) {
            int fx = GameView.foundationX(i);
            if (!model.getFoundations().get(i).isEmpty() &&
                    hitCard(mx, my, fx, GameView.CARD_SPACING)) {
                model.startDragFromFoundation(i);
                beginDrag(e, mx - fx, my - GameView.CARD_SPACING);
                return;
            }
        }

        // Tableau
        for (int col = 0; col < 7; col++) {
            List<GameModel.Card> pile = model.getTableau().get(col);
            if (pile.isEmpty()) continue;
            int tx = GameView.tableauX(col);

            for (int i = pile.size() - 1; i >= 0; i--) {
                GameModel.Card card = pile.get(i);
                int cardY = GameView.TABLEAU_Y + i * GameView.PILE_OFFSET;
                int cardH = (i == pile.size() - 1) ? GameView.CARD_HEIGHT : GameView.PILE_OFFSET;

                if (mx >= tx && mx <= tx + GameView.CARD_WIDTH &&
                        my >= cardY && my < cardY + cardH) {
                    if (card.isFaceUp()) {
                        model.startDragFromTableau(col, i);
                        beginDrag(e, mx - tx, my - cardY);
                        return;
                    }
                    break; // carta coperta cliccata: nessun drag
                }
            }
        }
    }

    private void beginDrag(MouseEvent e, int offX, int offY) {
        dragStart = e.getPoint();
        dragOffset.setLocation(offX, offY);
        syncDragToView(e.getPoint());
    }

    // ── Drag ─────────────────────────────────────────────────────────────────

    private void handleMouseDrag(MouseEvent e) {
        if (!model.getDraggedCards().isEmpty() && dragStart != null) {
            syncDragToView(e.getPoint());
        }
    }

    // ── Release (drop) ────────────────────────────────────────────────────────

    private void handleMouseRelease(MouseEvent e) {
        if (model.getDraggedCards().isEmpty()) return;

        // Rettangolo della prima carta trascinata nella posizione in cui viene rilasciata
        Rectangle dropRect = new Rectangle(
                e.getX() - dragOffset.x, e.getY() - dragOffset.y,
                GameView.CARD_WIDTH, GameView.CARD_HEIGHT);

        tryDrop(dropRect);

        model.clearDrag();
        dragStart = null;
        syncDragToView(null);
        refreshView();
    }

    /**
     * Sceglie la destinazione in base a quanto la carta trascinata si sovrappone
     * a fondamenta e colonne (non solo alla posizione del puntatore) e prova le
     * destinazioni dalla piu' sovrapposta alla meno sovrapposta.
     */
    private boolean tryDrop(Rectangle dropRect) {
        // {tipo (0=fondamenta, 1=tableau), indice, area di sovrapposizione}
        List<int[]> targets = new ArrayList<>();

        if (model.getDraggedCards().size() == 1) {
            for (int i = 0; i < 4; i++) {
                Rectangle r = new Rectangle(GameView.foundationX(i), GameView.CARD_SPACING,
                        GameView.CARD_WIDTH, GameView.CARD_HEIGHT);
                int area = overlapArea(dropRect, r);
                if (area > 0) targets.add(new int[]{0, i, area});
            }
        }

        for (int col = 0; col < 7; col++) {
            int size   = model.getTableau().get(col).size();
            int height = GameView.CARD_HEIGHT + Math.max(0, size - 1) * GameView.PILE_OFFSET;
            Rectangle r = new Rectangle(GameView.tableauX(col), GameView.TABLEAU_Y,
                    GameView.CARD_WIDTH, height);
            int area = overlapArea(dropRect, r);
            if (area > 0) targets.add(new int[]{1, col, area});
        }

        targets.sort((a, b) -> Integer.compare(b[2], a[2]));

        for (int[] t : targets) {
            if (t[0] == 0) {
                if (model.tryPlaceOnFoundation(t[1])) {
                    if (model.checkWin()) SwingUtilities.invokeLater(this::showVictory);
                    return true;
                }
            } else if (model.tryPlaceOnTableau(t[1])) {
                return true;
            }
        }
        return false;
    }

    private static int overlapArea(Rectangle a, Rectangle b) {
        Rectangle i = a.intersection(b);
        return i.isEmpty() ? 0 : i.width * i.height;
    }

    // ── Sincronizza stato drag con la View ───────────────────────────────────

    private void syncDragToView(Point mousePos) {
        view.gamePanel.setDragState(
                model.getDraggedCards(),
                dragStart,
                mousePos,
                model.getSourceTableau(),
                model.getSourceIndex()
        );
        view.gamePanel.repaint();
    }

    // ── Aggiorna etichette e ridisegna ───────────────────────────────────────

    private void refreshView() {
        view.updateTimerLabel(model.getElapsedSeconds());
        view.updateMovesLabel(model.getMoveCount());
        view.gamePanel.repaint();
    }

    private void showVictory() {
        view.showVictoryDialog(
                model.getElapsedSeconds(),
                model.getMoveCount(),
                model.getDifficulty()
        );
    }

    // ── Entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) { ex.printStackTrace(); }

            GameModel model = new GameModel();
            GameView  view  = new GameView();

            new GameController(model, view);

            view.pack();
            view.setLocationRelativeTo(null);
            view.setVisible(true);
        });
    }
}