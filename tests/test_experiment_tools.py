"""Regression tests for utilities. Bundled evidence is used only as test fixtures.
Temporary copies are used; these tests do not alter recorded experiment results.
"""
from pathlib import Path
import copy, csv, json, shutil, sys, tempfile, unittest
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'))
from campaign_tools import load_campaign, digest
from verify_results import audit_plan
from pilot_summary import summarize
from freeze_formal import freeze

class ToolTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='paqrap-tools-');self.work=Path(self.temp.name)
        self.campaign=self.work/'campaign';shutil.copytree(ROOT/'evidencia/flex1/mini-pilot',self.campaign)
        # Test fixture follows current build; this does not relabel actual measured evidence.
        meta=json.loads((self.campaign/'metadata.json').read_text());meta['classpath_sha256']['paqrap-experimentos.jar']=digest(ROOT/'dist/paqrap-experimentos.jar')
        (self.campaign/'metadata.json').write_text(json.dumps(meta))
    def tearDown(self): self.temp.cleanup()
    def mutate_rows(self, fn):
        p=self.campaign/'runs.csv'
        with p.open(newline='') as f: reader=csv.DictReader(f);headers=reader.fieldnames;rows=list(reader)
        rows=fn(rows)
        with p.open('w',newline='') as f:writer=csv.DictWriter(f,headers);writer.writeheader();writer.writerows(rows)
    def test_complete_grid(self):self.assertEqual(8,len(load_campaign(self.campaign)[1]))
    def test_fixed_mode_has_no_time_budget(self):self.assertEqual(4,len(load_campaign(ROOT/'evidencia/flex1/deterministic-a')[1]))
    def test_reject_duplicate(self):
        self.mutate_rows(lambda r:r+[r[0]])
        with self.assertRaises(ValueError):load_campaign(self.campaign)
    def test_reject_missing_run(self):
        self.mutate_rows(lambda r:r[:-1])
        with self.assertRaises(ValueError):load_campaign(self.campaign)
    def test_reject_mixed_config(self):
        def edit(r):r[0]['config_sha256']='OTHER';return r
        self.mutate_rows(edit)
        with self.assertRaises(ValueError):load_campaign(self.campaign)
    def test_reject_cost_for_incomplete(self):
        def edit(r):next(x for x in r if x['full_feasible']=='false')['cost_complete_feasible']='1';return r
        self.mutate_rows(edit)
        with self.assertRaises(ValueError):load_campaign(self.campaign)
    def test_balanced_cost_subset(self):
        result=summarize(self.campaign);self.assertEqual(4,len(result))
        self.assertTrue(all(r['cost_balanced_pairs']==1 for r in result))
    def test_freeze_valid(self):
        p=self.work/'formal.properties';info=freeze(self.campaign,10000,p,'Prueba unitaria del mecanismo de congelación de parámetros.',ROOT/'config/formal.properties')
        self.assertEqual(400,info['formal_runs']);self.assertTrue(p.with_suffix('.freeze.json').exists())
    def test_freeze_rejects_untried_budget(self):
        with self.assertRaises(ValueError):freeze(self.campaign,5000,self.work/'formal.properties','Prueba unitaria con presupuesto no calibrado.',ROOT/'config/formal.properties')
    def test_freeze_refuses_overwrite(self):
        p=self.work/'formal.properties';p.write_text('existing')
        with self.assertRaises(ValueError):freeze(self.campaign,10000,p,'Prueba unitaria que no debe sobrescribir nada.',ROOT/'config/formal.properties')
    def fixture_plan(self):
        _,rows,_=load_campaign(self.campaign);row=rows[0]
        manifest=json.loads((self.campaign/'instances'/f"{row['instance_id']}.json").read_text())
        plan=json.loads((self.campaign/'jobs'/f"{row['run_id']}.plan.json").read_text())
        return manifest,plan,row
    def test_independent_audit_valid(self):
        m,p,r=self.fixture_plan();self.assertTrue(audit_plan(m,p,r)['complete'])
    def test_independent_audit_rejects_missing_break(self):
        m,p,r=self.fixture_plan();p['audit']['routes'][0]['meal_breaks']=[]
        with self.assertRaises(ValueError):audit_plan(m,p,r)
    def test_independent_audit_rejects_wrong_break_location(self):
        m,p,r=self.fixture_plan();p['audit']['routes'][0]['meal_breaks'][0]['x']=71
        with self.assertRaises(ValueError):audit_plan(m,p,r)
    def test_independent_audit_rejects_short_service(self):
        m,p,r=self.fixture_plan();t=p['audit']['routes'][0]['timetable'][0];t['completion']=t['service_start']
        with self.assertRaises(ValueError):audit_plan(m,p,r)

if __name__=='__main__':unittest.main()
