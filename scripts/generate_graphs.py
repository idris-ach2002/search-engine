import pandas as pd
import matplotlib.pyplot as plt
import numpy as np

plt.style.use('ggplot')

try:
    df_jaccard = pd.read_csv("bench_jaccard.csv")
    df_pagerank = pd.read_csv("bench_pagerank.csv")
    df_search = pd.read_csv("bench_search.csv")
    df_zipf = pd.read_csv("bench_zipf.csv")
except FileNotFoundError:
    print("Erreur : Fichiers CSV introuvables. Lance d'abord le Benchmark.java.")
    exit()

# --- GRAPHIQUE 1 : Scalabilité Jaccard ---
plt.figure(figsize=(8, 5))
plt.plot(df_jaccard['livres'], df_jaccard['temps_ms'], marker='o', color='#6a1b9a', linewidth=2)
plt.title("Temps d'exécution de Jaccard en fonction du nombre de livres")
plt.xlabel("Nombre de livres")
plt.ylabel("Temps (ms)")
plt.grid(True, linestyle='--', alpha=0.7)
plt.tight_layout()
plt.savefig("graph_jaccard.png", dpi=300)
plt.close()

# --- GRAPHIQUE 2 : Convergence PageRank ---
plt.figure(figsize=(8, 5))
plt.plot(df_pagerank['iteration'], df_pagerank['delta'], marker='s', color='#e65100', linestyle='--')
plt.yscale('log')
plt.title("Convergence de l'algorithme PageRank")
plt.xlabel("Itération")
plt.ylabel("Erreur (Delta) - échelle log")
plt.grid(True, linestyle='--', alpha=0.7)
plt.tight_layout()
plt.savefig("graph_pagerank.png", dpi=300)
plt.close()

# --- GRAPHIQUE 3 : KMP vs RegEx ---
kmp_data = df_search[df_search['methode'] == 'KMP']
regex_data = df_search[df_search['methode'] == 'REGEX']

fig, ax = plt.subplots(figsize=(10, 5))
x = np.arange(len(kmp_data))
width = 0.35

ax.bar(x - width/2, kmp_data['temps_ms'], width, label='KMP', color='#1976d2')
ax.bar(x + width/2, regex_data['temps_ms'], width, label='RegEx', color='#bf360c')

ax.set_ylabel('Temps (ms)')
ax.set_title('Comparaison des temps de recherche (KMP vs RegEx)')
ax.set_xticks(x)
ax.set_xticklabels(kmp_data['requete'])
ax.legend()
plt.tight_layout()
plt.savefig("graph_search.png", dpi=300)
plt.close()

# --- GRAPHIQUE 4 : Loi de Zipf ---
plt.figure(figsize=(8, 5))
plt.plot(df_zipf['rang'], df_zipf['frequence'], color='#2e7d32', linewidth=2)
plt.yscale('log')
plt.xscale('log')
plt.title("Distribution des fréquences des mots (Loi de Zipf)")
plt.xlabel("Rang du mot (échelle log)")
plt.ylabel("Fréquence (échelle log)")
plt.grid(True, linestyle='--', alpha=0.7)
plt.tight_layout()
plt.savefig("graph_zipf.png", dpi=300)
plt.close()

print("Tous les graphiques ont été générés avec succès dans le dossier courant.")