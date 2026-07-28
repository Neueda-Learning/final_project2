import type { Instrument } from "../api/types";
import { CURATED_SECTORS, curatedSector, type CuratedSectorId } from "../data/curatedInstruments";
import { useLanguage } from "../i18n/LanguageContext";

interface CuratedInstrumentPickerProps {
  instruments: Instrument[];
  selectedInstrument: Instrument | null;
  sectorId: CuratedSectorId;
  onSectorChange: (sectorId: CuratedSectorId) => void;
  onSelect: (instrument: Instrument) => void;
}

export function CuratedInstrumentPicker({
  instruments,
  selectedInstrument,
  sectorId,
  onSectorChange,
  onSelect,
}: CuratedInstrumentPickerProps) {
  const { t } = useLanguage();
  const activeSector = curatedSector(sectorId);
  const instrumentsBySymbol = new Map(instruments.map((instrument) => [instrument.symbol, instrument]));
  const sectorInstruments = activeSector.symbols
    .map((symbol) => instrumentsBySymbol.get(symbol))
    .filter((instrument): instrument is Instrument => Boolean(instrument));

  return (
    <div className="curated-picker">
      <div className="curated-picker__intro">
        <div>
          <span className="curated-picker__eyebrow">{t("holdings.curatedEyebrow")}</span>
          <h3>{t("holdings.curatedTitle")}</h3>
          <p>{t("holdings.curatedDescription")}</p>
        </div>
        <span className="curated-picker__guard">
          <span aria-hidden="true">✓</span>
          {t("holdings.curatedGuard")}
        </span>
      </div>

      <div className="sector-tabs" role="tablist" aria-label={t("holdings.sectorLabel")}>
        {CURATED_SECTORS.map((sector) => {
          const isActive = sector.id === sectorId;
          return (
            <button
              type="button"
              role="tab"
              aria-selected={isActive}
              key={sector.id}
              className={`sector-tab sector-tab--${sector.accent}${isActive ? " is-active" : ""}`}
              onClick={() => onSectorChange(sector.id)}
            >
              <span className="sector-tab__icon" aria-hidden="true">{sector.icon}</span>
              <span className="sector-tab__copy">
                <strong>{t(`holdings.sector.${sector.id}`)}</strong>
                <small>{t(`holdings.sector.${sector.id}.hint`)}</small>
              </span>
              <span className="sector-tab__count">10</span>
            </button>
          );
        })}
      </div>

      <div className="instrument-choice-grid" role="listbox" aria-label={t("holdings.instrumentChoices")}>
        {sectorInstruments.map((instrument) => {
          const isSelected = selectedInstrument?.id === instrument.id;
          return (
            <button
              type="button"
              role="option"
              aria-selected={isSelected}
              key={instrument.id}
              className={`instrument-choice${isSelected ? " is-selected" : ""}`}
              onClick={() => onSelect(instrument)}
            >
              <span className="instrument-choice__mark">{instrument.symbol.slice(0, 2)}</span>
              <span className="instrument-choice__copy">
                <span className="instrument-choice__topline">
                  <strong>{instrument.symbol}</strong>
                  <small>{instrument.assetType}</small>
                </span>
                <span className="instrument-choice__name">{instrument.name}</span>
                <span className="instrument-choice__exchange">{instrument.exchangeCode}</span>
              </span>
              <span className="instrument-choice__check" aria-hidden="true">
                {isSelected ? "✓" : "＋"}
              </span>
            </button>
          );
        })}
      </div>

      {sectorInstruments.length < 10 ? (
        <p className="curated-picker__notice">{t("holdings.curatedUnavailable")}</p>
      ) : null}
    </div>
  );
}
