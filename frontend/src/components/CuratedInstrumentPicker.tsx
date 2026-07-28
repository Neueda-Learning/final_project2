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
      <div className="curated-picker__bar">
        <div className="curated-picker__label">
          <span>1</span>
          <strong>{t("holdings.curatedTitle")}</strong>
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
              <strong>{t(`holdings.sector.${sector.id}`)}</strong>
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
              title={`${instrument.name} · ${instrument.exchangeCode}`}
              onClick={() => onSelect(instrument)}
            >
              <strong>{instrument.symbol}</strong>
              <small>{instrument.assetType}</small>
              <span className="instrument-choice__check" aria-hidden="true">
                {isSelected ? "✓" : ""}
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
